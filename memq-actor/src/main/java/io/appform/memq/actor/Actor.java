package io.appform.memq.actor;

import com.codahale.metrics.MetricRegistry;
import com.google.common.base.Stopwatch;
import com.google.common.collect.Sets;
import io.appform.memq.retry.RetryStrategy;
import io.appform.memq.stats.ActorMetrics;
import io.appform.memq.stats.impl.ActorMetricsImpl;
import lombok.extern.slf4j.Slf4j;
import lombok.val;

import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Slf4j
public class Actor<M extends Message> implements AutoCloseable {

    private final String name;
    private final ExecutorService executorService;
    private final ExecutorService messageDispatcher; //TODO::Separate dispatch and add NoDispatch flow
    private final ToIntFunction<M> partitioner;
    private final Map<Integer, UnboundedMailbox<M>> mailboxes;
    private final Function<M,Boolean> validationHandler;
    private final Function<M,Boolean> consumerHandler;
    private final Consumer<M> sidelineHandler;
    private final BiConsumer<M,Throwable> exceptionHandler;
    private final RetryStrategy retryer;
    private final ActorMetrics metrics;


    public Actor(String name,
                 ExecutorService executorService,
                 Function<M, Boolean> validationHandler,
                 Function<M, Boolean> consumerHandler,
                 Consumer<M> sidelineHandler,
                 BiConsumer<M, Throwable> exceptionHandler,
                 RetryStrategy retryer,
                 int partitions,
                 ToIntFunction<M> partitioner,
                 MetricRegistry metricRegistry) {
        Objects.requireNonNull(name, "Name cannot be null");
        Objects.requireNonNull(executorService, "Executor service cannot be null");
        Objects.requireNonNull(partitioner, "Partitioner cannot be null");
        Objects.requireNonNull(validationHandler, "ValidationHandler cannot be null");
        Objects.requireNonNull(consumerHandler, "ConsumerHandler cannot be null");
        Objects.requireNonNull(sidelineHandler, "SidelineHandler cannot be null");
        Objects.requireNonNull(exceptionHandler, "ExceptionHandler cannot be null");
        Objects.requireNonNull(metricRegistry, "Metric Registry cannot be null");
        this.name = name;
        this.executorService = executorService;
        this.validationHandler = validationHandler;
        this.consumerHandler = consumerHandler;
        this.sidelineHandler = sidelineHandler;
        this.exceptionHandler = exceptionHandler;
        this.messageDispatcher = Executors.newFixedThreadPool(partitions);
        this.retryer = retryer;
        this.partitioner = partitioner;
        this.mailboxes = IntStream.range(0, partitions)
                .boxed()
                .collect(Collectors.toMap(Function.identity(), i -> new UnboundedMailbox<M>(this, i)));
        this.metrics = new ActorMetricsImpl(metricRegistry, name, this::size);
    }

    public final boolean isEmpty() {
        return mailboxes.values()
                .stream()
                .allMatch(UnboundedMailbox::isEmpty);
    }

    public final long size() {
        return mailboxes.values()
                .stream()
                .map(UnboundedMailbox::size)
                .count();
    }

    public final boolean publish(final M message) {
        return Optional.of(mailboxes.get(partitioner.applyAsInt(message)))
                .map(queue -> queue.publish(message))
                .map( res -> {
                    if (res) {
                        metrics.markPublishSuccess();
                    } else {
                        metrics.markPublishFailed();
                    }
                    return res;
                })
                .orElse(false);
    }

    public final void start() {
        mailboxes.values().forEach(UnboundedMailbox::start);
    }

    @Override
    public final void close() {
        mailboxes.values().forEach(UnboundedMailbox::close);
    }


    private static class UnboundedMailbox<M extends Message> implements AutoCloseable {

        private final Actor<M> actor;
        private final String name;
        private final ReentrantLock lock = new ReentrantLock();
        private final Condition checkCondition = lock.newCondition();
        private final Map<String, M> messages = new HashMap<>();
        private final Set<String> inFlight = new HashSet<>();
        private final AtomicBoolean stopped = new AtomicBoolean();
        private Future<?> monitorFuture;

        public UnboundedMailbox(Actor<M> actor, int partition) {
            this.actor = actor;
            this.name = actor.name + "-" + partition;
        }

        public final boolean isEmpty() {
            lock.lock();
            try {
                return messages.isEmpty();
            } finally {
                lock.unlock();
            }
        }

        public final long size() {
            return messages.size();
        }

        public final void start() {
            monitorFuture = actor.messageDispatcher.submit(this::monitor);
        }

        public final boolean publish(final M message) {
            lock.lock();
            try {
                val messageId = message.id();
                messages.putIfAbsent(messageId, message);
                checkCondition.signalAll();
                return true;
            } finally {
                lock.unlock();
            }
        }


        @Override
        public final void close() {
            lock.lock();
            try {
                stopped.set(true);
                checkCondition.signalAll();
            } finally {
                lock.unlock();
            }
            if (null != monitorFuture) {
                monitorFuture.cancel(true);
            }
        }

        private void monitor() {
            lock.lock();
            try {
                while (true) {
                    checkCondition.await();
                    if (stopped.get()) {
                        log.info("Actor {} monitor thread exiting", name);
                        return;
                    }
                    //Find new messages
                    val newMessages = Set.copyOf(Sets.difference(messages.keySet(), inFlight));
                    if (newMessages.isEmpty()) {
                        log.trace("No new messages. Ignoring spurious wakeup.");
                        continue;
                    }
                    inFlight.addAll(newMessages);
                    newMessages.forEach(m -> actor.executorService.submit(() -> {
                        try {
                            this.process(messages.get(m));
                        } catch (Throwable throwable) {
                            log.error("Error", throwable);
                        }
                    }));
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.warn("Monitor thread interrupted for {}", name);
            } finally {
                lock.unlock();
            }
        }

        private void process(final M message) {
            final Stopwatch stopwatch = Stopwatch.createStarted();
            actor.metrics.markProcessing();
            val id = message.id();
            try {
                boolean valid = actor.validationHandler.apply(message);
                if (!valid) {
                    log.debug("Message validation failed for message: {}", message);
                } else {
                    val status = actor.retryer.execute(() -> actor.consumerHandler.apply(message));
                    if (!status) {
                        log.debug("Consumer failed for message: {}", message);
                        actor.metrics.markSideline();
                        actor.sidelineHandler.accept(message);
                    }
                }
            } catch (Exception e) {
                actor.exceptionHandler.accept(message, e);
            } finally {
                releaseMessage(id);
                stopwatch.stop();
                actor.metrics.markReleased();
                actor.metrics.updateProcessTime(stopwatch.elapsed());
            }
        }

        private void releaseMessage(String id) {
            lock.lock();
            try {
                inFlight.remove(id);
                messages.remove(id);
                checkCondition.signalAll(); //Redeliver
            } finally {
                lock.unlock();
            }
        }
    }
}

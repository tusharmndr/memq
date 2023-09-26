package io.appform.memq.actor;

import com.google.common.collect.Sets;
import io.appform.memq.exceptionhandler.handlers.ExceptionHandler;
import io.appform.memq.retry.RetryStrategy;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import net.jodah.failsafe.internal.util.Assert;

import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Function;
import java.util.function.ToIntFunction;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 *
 */
@Slf4j
public abstract class Actor<M extends Message> implements AutoCloseable {
    private final ExecutorService executorService;
    private final Set<Class<? extends Throwable>> ignoredErrors; //TODO: Irrelevant as there is no serialization/deserialization
    private final ExecutorService dispatcher; //TODO::Separate dispatch and add NoDispatch flow
    private final ToIntFunction<M> partitioner;
    private final Map<Integer, PartitionQueue<M>> queues; //TODO: Separate is as Mailbox, and take PartitionMailbox by default
    private final RetryStrategy retryer;
    private final ExceptionHandler exceptionHandler;
    //TODO: sidelineExecutor
    //TODO: expiry message
    //TODO: Metrics


    protected Actor(ExecutorService executorService,
                    RetryStrategy retryer,
                    ExceptionHandler exceptionHandler,
                    Set<Class<? extends Throwable>> ignoredErrors,
                    int partitions,
                    ToIntFunction<M> partitioner) {
        Assert.notNull(executorService, "Executor service cannot be null");
        Assert.notNull(retryer, "Retryer cannot be null");
        Assert.notNull(exceptionHandler, "ExceptionHandler cannot be null");
        Assert.notNull(partitioner, "Partitioner cannot be null");
        this.executorService = executorService;
        this.ignoredErrors = null != ignoredErrors ? ignoredErrors : Set.of();
        this.retryer = retryer;
        this.exceptionHandler = exceptionHandler;
        this.dispatcher = Executors.newFixedThreadPool(partitions);
        this.partitioner = partitioner;
        this.queues = IntStream.range(0, partitions)
                .boxed()
                .collect(Collectors.toMap(Function.identity(), i -> new PartitionQueue<M>(this, i)));
    }

    public abstract String name();

    public boolean isEmpty() {
        return queues.values()
                .stream()
                .allMatch(PartitionQueue::isEmpty);
    }

    public final boolean publish(final M message) {
        return Optional.of(queues.get(partitioner.applyAsInt(message)))
                .map(queue -> queue.publish(message))
                .orElse(false);
    }

    public void start() {
        queues.values().forEach(PartitionQueue::start);
    }

    @Override
    public void close() {
        queues.values().forEach(PartitionQueue::close);
    }

    protected abstract boolean handleMessage(final M message) throws Exception;

    protected abstract void sidelineMessage(final M message) throws Exception;


    private static class PartitionQueue<M extends Message> implements AutoCloseable {

        private final Actor<M> actor;
        private final String name;
        private final ReentrantLock lock = new ReentrantLock();
        private final Condition checkCondition = lock.newCondition();
        private final Map<String, M> messages = new HashMap<>();
        private final Set<String> inFlight = new HashSet<>();
        private final AtomicBoolean stopped = new AtomicBoolean();
        private Future<?> monitorFuture;

        public PartitionQueue(Actor<M> actor, int partition) {
            this.actor = actor;
            this.name = actor.name() + "-" + partition;
        }

        public final boolean isEmpty() {
            lock.lock();
            try {
                return messages.isEmpty();
            } finally {
                lock.unlock();
            }
        }

        public final void start() {
            monitorFuture = actor.dispatcher.submit(this::monitor);
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
                            this.handleMessageInternal(messages.get(m));
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

        private void handleMessageInternal(final M message) {
            val id = message.id();
            var status = false;
            try {
                status = actor.retryer.execute(() -> actor.handleMessage(message));
            } catch (Exception e) {
                log.error("Error handling message: " + message.id(), e);
                status = actor.exceptionHandler.handle();
            }
            if (!status) {
                try {
                    actor.sidelineMessage(message);
                } catch (Exception e) {
                    log.error("Error while sidelining message: " + message.id(), e);
                }
            }
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

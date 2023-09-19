package io.appform.memq;

import com.google.common.collect.Sets;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import net.jodah.failsafe.Failsafe;
import net.jodah.failsafe.RetryPolicy;
import org.apache.commons.lang3.ClassUtils;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 *
 */
@Slf4j
public abstract class Actor<M extends Message> implements AutoCloseable {
    private final ExecutorService executorService;
    private final Set<Class<? extends Throwable>> ignoredErrors;
    private final ExecutorService dispachter;
    private final Function<M, Integer> partitioner;
    private final Map<Integer, PartitionQueue<M>> queues;



    protected Actor(ExecutorService executorService,
                 Set<Class<? extends Throwable>> ignoredErrors) {
        this(executorService, ignoredErrors,
                1, i -> 0);
    }

    protected Actor(ExecutorService executorService,
                    Set<Class<? extends Throwable>> ignoredErrors,
                    int partitions,
                    Function<M, Integer> partitioner) {
        this.executorService = executorService;
        this.ignoredErrors = null != ignoredErrors ? ignoredErrors : Set.of();
        this.dispachter = Executors.newFixedThreadPool(partitions);
        this.partitioner = partitioner;
        this.queues = IntStream.range(0,partitions)
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
        return Optional.of(queues.get(partitioner.apply(message)))
                .map(queue -> queue.publish(message))
                .orElse(false);
    }

    public void start() {
        queues.values().forEach(PartitionQueue::start);
    }

    protected abstract boolean handleMessage(final M message) throws Exception;

    @Override
    public void close() {
        queues.values().forEach(PartitionQueue::close);
    }


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
            monitorFuture = actor.dispachter.submit(this::monitor);
        }

        public final boolean publish(final M message) {
            lock.lock();
            try {
                val messageId = message.id();
                messages.putIfAbsent(messageId, message);
                checkCondition.signalAll();
                return true;
            }
            finally {
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
            val policy = new RetryPolicy<Boolean>()
                    .handleIf(t -> actor.ignoredErrors.stream().anyMatch(e -> ClassUtils.isAssignable(t.getClass(), e)))
                    .withMaxAttempts(3)
                    .withDelay(Duration.ofSeconds(1));
            var status = false;
            try {
                status = Failsafe.with(List.of(policy))
                        .get(() -> actor.handleMessage(message));
            } catch (Exception e) {
                log.error("Error handling message: " + message.id(), e);
            }
            lock.lock();
            try {
                inFlight.remove(id);
                if (status) {
                    messages.remove(id);
                } else {
                    checkCondition.signalAll(); //Redeliver
                }
            } finally {
                lock.unlock();
            }
        }
    }
}

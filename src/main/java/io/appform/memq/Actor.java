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
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

/**
 *
 */
@Slf4j
public abstract class Actor<M extends Message> implements AutoCloseable {
    private final ReentrantLock lock = new ReentrantLock();
    private final Condition checkCondition = lock.newCondition();
    private final Map<String, M> messages = new HashMap<>();
    private final Set<String> inFlight = new HashSet<>();
    private final AtomicBoolean stopped = new AtomicBoolean();
    private final ExecutorService executorService;
    private final Set<Class<? extends Throwable>> ignoredErrors;
    private Future<?> monitorFuture;


    protected Actor(ExecutorService executorService, Set<Class<? extends Throwable>> ignoredErrors) {
        this.executorService = executorService;
        this.ignoredErrors = null != ignoredErrors ? ignoredErrors : Set.of();
    }


    public abstract String name();

    public boolean isEmpty() {
        lock.lock();
        try {
            return messages.isEmpty();
        }
        finally {
            lock.unlock();
        }
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

    public void start() {
        monitorFuture = executorService.submit(this::monitor);
    }

    private void monitor() {
        lock.lock();
        try {
            while (true) {
                checkCondition.await();
                if(stopped.get()) {
                    log.info("Actor {} monitor thread exiting", name());
                    return;
                }
                //Find new messages
                val newMessages = Set.copyOf(Sets.difference(messages.keySet(), inFlight));
                if (newMessages.isEmpty()) {
                    log.trace("No new messages. Ignoring spurious wakeup.");
                    continue;
                }
                inFlight.addAll(newMessages);
                newMessages.forEach(m -> executorService.submit(() -> {
                    try {
                        this.handleMessageInternal(messages.get(m));
                    }
                    catch (Throwable throwable) {
                        log.error("Error", throwable);
                    }
                }));
            }
        }
        catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Monitor thread interrupted for {}", name());
        }
        finally {
            lock.unlock();
        }
    }

    protected final void handleMessageInternal(final M message) {
        val id = message.id();
        val policy = new RetryPolicy<Boolean>()
                .handleIf(t -> ignoredErrors.stream().anyMatch(e -> ClassUtils.isAssignable(t.getClass(), e)))
                .withMaxAttempts(3)
                .withDelay(Duration.ofSeconds(1));
        var status = false;
        try {
            status = Failsafe.with(List.of(policy))
                    .get(() -> handleMessage(message));
        }
        catch (Exception e) {
            log.error("Error handling message: " + message.id(), e);
        }
        lock.lock();
        try {
            inFlight.remove(id);
            if (status) {
                messages.remove(id);
            }
            else {
                checkCondition.signalAll(); //Redeliver
            }
        }
        finally {
            lock.unlock();
        }
    }

    protected abstract boolean handleMessage(final M message) throws Exception;

    @Override
    public void close() {
        lock.lock();
        try {
            stopped.set(true);
            checkCondition.signalAll();
        }
        finally {
            lock.unlock();
        }
        if (null != monitorFuture) {
            monitorFuture.cancel(true);
        }
    }
}

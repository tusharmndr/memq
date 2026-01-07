package io.appform.memq.actor;

import lombok.extern.slf4j.Slf4j;
import lombok.val;

import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.Condition;
import java.util.function.Function;

@Slf4j
class AsyncDispatcherWorker<M extends Message> implements AutoCloseable {
    private final Mailbox<M> mailbox;
    private final Dispatcher<M> dispatcher;
    private final AtomicBoolean stopped;
    private final Condition checkCondition;
    private Future<?> monitoredFuture;

    public AsyncDispatcherWorker(Mailbox<M> inMailbox, Dispatcher<M> inDispatcher) {
        mailbox = inMailbox;
        dispatcher = inDispatcher;
        checkCondition = inMailbox.lockNewCondition();
        stopped = new AtomicBoolean(false);
    }

    //Always executed inside mailbox lock
    public final void start(final Function<Runnable, Future<?>> taskSubmitter) {
        monitoredFuture = taskSubmitter.apply(this::monitor);
    }

    //Always executed inside mailbox lock
    public final void close() {
        stopped.set(true);
        checkCondition.signalAll();
        monitoredFuture.cancel(true);
    }

    //Always executed inside mailbox lock
    public final void trigger() {
        checkCondition.signalAll();
    }

    public final boolean isRunning() {
        return !stopped.get();
    }

    private void monitor() {
        val name = mailbox.getName();
        mailbox.acquireLock();
        try {
            while (true) {
                checkCondition.await(100, TimeUnit.MILLISECONDS);
                if (stopped.get()) {
                    log.info("Actor {} monitor thread exiting", name);
                    return;
                }
                dispatcher.dispatch(mailbox);
            }
        }
        catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Monitor thread stopped for {}", name);
        }
        finally {
            mailbox.releaseLock();
        }
    }
}


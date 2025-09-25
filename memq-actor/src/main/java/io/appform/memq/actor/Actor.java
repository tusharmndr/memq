package io.appform.memq.actor;

import com.google.common.base.Preconditions;
import com.google.common.collect.Sets;
import io.appform.memq.observer.ActorObserver;
import io.appform.memq.observer.ActorObserverContext;
import io.appform.memq.observer.ObserverMessageMeta;
import io.appform.memq.observer.TerminalActorObserver;
import io.appform.memq.retry.RetryStrategy;
import io.appform.memq.utils.TriConsumer;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import lombok.val;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.ToIntFunction;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Slf4j
public class Actor<M extends Message> implements AutoCloseable {

    private final String name;
    private final ExecutorService executorService;
    private final Dispatcher<M> messageDispatcher;
    private final ToIntFunction<M> partitioner;
    private final Map<Integer, Mailbox<M>> mailboxes;
    private final BiFunction<M, MessageMeta, Boolean> validationHandler;
    private final BiFunction<M, MessageMeta, Boolean> consumerHandler;
    private final BiConsumer<M, MessageMeta> sidelineHandler;
    private final TriConsumer<M, MessageMeta, Throwable> exceptionHandler;
    private final RetryStrategy retryer;
    private final ActorObserver rootObserver;


    @SneakyThrows
    public Actor(
            String name,
            ExecutorService executorService,
            BiFunction<M, MessageMeta, Boolean> validationHandler,
            BiFunction<M, MessageMeta, Boolean> consumerHandler,
            BiConsumer<M, MessageMeta> sidelineHandler,
            TriConsumer<M, MessageMeta, Throwable> exceptionHandler,
            RetryStrategy retryer,
            DispatcherType dispatcherType,
            int partitions,
            long maxSizePerPartition,
            int maxConcurrencyPerPartition,
            ToIntFunction<M> partitioner,
            List<ActorObserver> observers) {
        Objects.requireNonNull(name, "Name cannot be null");
        Objects.requireNonNull(executorService, "Executor service cannot be null");
        Objects.requireNonNull(partitioner, "Partitioner cannot be null");
        Objects.requireNonNull(validationHandler, "ValidationHandler cannot be null");
        Objects.requireNonNull(consumerHandler, "ConsumerHandler cannot be null");
        Objects.requireNonNull(sidelineHandler, "SidelineHandler cannot be null");
        Objects.requireNonNull(exceptionHandler, "ExceptionHandler cannot be null");
        Preconditions.checkArgument(
                dispatcherType != DispatcherType.SYNC || maxConcurrencyPerPartition == maxSizePerPartition,
                        "Max Queue size and max concurrency has to be same for sync dispatcher");
        this.name = name;
        this.executorService = executorService;
        this.validationHandler = validationHandler;
        this.consumerHandler = consumerHandler;
        this.sidelineHandler = sidelineHandler;
        this.exceptionHandler = exceptionHandler;
        this.retryer = retryer;
        this.partitioner = partitioner;
        this.messageDispatcher = dispatcher(dispatcherType, partitions);
        this.mailboxes = IntStream.range(0, partitions)
                .boxed()
                .collect(Collectors.toMap(Function.identity(), i -> new Mailbox<M>(this, i, maxSizePerPartition, maxConcurrencyPerPartition)));
        this.rootObserver = setupObserver(observers);
    }

    public final boolean isEmpty() {
        return mailboxes.values()
                .stream()
                .allMatch(Mailbox::isEmpty);
    }

    public final long size() {
        return mailboxes.values()
                .stream()
                .mapToLong(Mailbox::size)
                .sum();
    }

    public final long inFlight() {
        return mailboxes.values()
                .stream()
                .mapToLong(Mailbox::inFlight)
                .sum();
    }

    public final boolean isRunning() {
        return messageDispatcher.isRunning();
    }

    public final void purge() {
        mailboxes.values().forEach(Mailbox::purge);
    }

    public final boolean publish(final M message) {
        return rootObserver.execute(ActorObserverContext.builder()
                                     .operation(ActorOperation.PUBLISH)
                                     .message(message)
                                     .actorName(name)
                                     .build(),
                             () -> mailboxes.get(partitioner.applyAsInt(message))
                                     .publish(message));
    }

    public final void start() {
        mailboxes.values().forEach(Mailbox::start);
    }

    @Override
    public final void close() {
        mailboxes.values().forEach(Mailbox::close);
        messageDispatcher.close();
    }

    private void processWithObserver(final InternalMessage<M> internalMessage) {
        val observerMessageMeta = new ObserverMessageMeta(internalMessage.getId(), internalMessage.getPublishedAt(),
                internalMessage.getValidTill());
        this.rootObserver.execute(ActorObserverContext.builder()
                        .messageMeta(observerMessageMeta)
                        .message(internalMessage.getMessage())
                        .operation(ActorOperation.CONSUME)
                        .actorName(this.name)
                        .build(),
                () -> process(internalMessage));
    }

    private boolean process(final InternalMessage<M> internalMessage) {
        val id = internalMessage.getId();
        val message = internalMessage.getMessage();
        var status = false;
        var messageMeta = new MessageMeta(internalMessage.getPublishedAt(),
                internalMessage.getValidTill(),
                internalMessage.getHeaders());
        try {
            val valid = this.rootObserver.execute(ActorObserverContext.builder()
                            .message(message)
                            .operation(ActorOperation.VALIDATE)
                            .actorName(this.name)
                            .build(),
                    () -> this.validationHandler.apply(message, messageMeta));
            if (!valid) {
                log.debug("Message validation failed for message: {}", message);
                return false;
            }
            else {
                status = this.retryer.execute(() -> {
                    messageMeta.incrementAttempt();
                    return this.consumerHandler.apply(message, messageMeta);
                });
                if (!status) {
                    log.debug("Consumer failed for message: {}", message);
                    this.rootObserver.execute(ActorObserverContext.builder()
                                    .message(message)
                                    .operation(ActorOperation.SIDELINE)
                                    .actorName(this.name)
                                    .build(),
                            () -> {
                                this.sidelineHandler.accept(message, messageMeta);
                                return true;
                            });
                }
            }
        } catch (Exception e) {
            log.error("Error processing message : " + id, e);
            this.rootObserver.execute(ActorObserverContext.builder()
                            .message(message)
                            .operation(ActorOperation.HANDLE_EXCEPTION)
                            .actorName(this.name)
                            .build(),
                    () -> {
                        this.exceptionHandler.accept(message, messageMeta, e);
                        return true;
                    });
        }
        return status;
    }

    private ActorObserver setupObserver(List<ActorObserver> observers) {
        //Terminal observer calls the actual method
        ActorObserver startObserver = new TerminalActorObserver();
        startObserver.initialize(this); //initializing terminal observer
        if (observers != null) {
            for (var observer : observers) {
                if (null == observer) {
                    continue;
                }
                startObserver = observer.setNext(startObserver);
                startObserver.initialize(this); //initializing new observer
            }
        }
        return startObserver;
    }

    private Dispatcher<M> dispatcher(final DispatcherType dispatcherType, final int partitions) {
        return switch (dispatcherType) {
            case SYNC -> new SyncDispatcher<>(partitions);
            case ASYNC_ISOLATED -> new AsyncIsolatedThreadpoolDispatcher<>(partitions);
        };
    }

    private static class Mailbox<M extends Message> implements AutoCloseable {

        private final Actor<M> actor;
        private final int partition;
        private final String name;
        private final long maxSize;
        private final int maxConcurrency;
        private final ReentrantLock lock = new ReentrantLock();
        private final Map<String, InternalMessage<M>> messages = new LinkedHashMap<>();
        private final Set<String> inFlight = new HashSet<>();

        public Mailbox(Actor<M> actor, int partition, long maxSize, int maxConcurrency) {
            this.actor = actor;
            this.maxSize = maxSize;
            this.maxConcurrency = maxConcurrency;
            this.partition = partition;
            this.name = actor.name + "-" + partition;
        }

        public final boolean isEmpty() {
            acquireLock();
            try {
                return messages.isEmpty();
            }
            finally {
                releaseLock();
            }
        }

        public final long size() {
            acquireLock();
            try {
                return messages.size();
            } finally {
                releaseLock();
            }
        }


        public final int inFlight() {
            acquireLock();
            try {
                return inFlight.size();
            } finally {
                releaseLock();
            }
        }

        public final void purge() {
            acquireLock();
            try {
                messages.clear();
            } finally {
                releaseLock();
            }
        }

        public final void start() {
            acquireLock();
            try {
                actor.messageDispatcher.register(this);
            }
            finally {
                releaseLock();
            }
        }

        public final boolean publish(final M message) {
            acquireLock();
            try {
                val currSize = messages.size();
                if (currSize >= maxSize) {
                    log.warn("Blocking publish for as curr size:{} is more than specified threshold:{}",
                            currSize, maxSize);
                    return false;
                }
                val internalMessage = new InternalMessage<>(message.id(), message.validTill(),
                        System.currentTimeMillis(), message.headers(), message);
                messages.putIfAbsent(internalMessage.getId(), internalMessage);
                actor.messageDispatcher.triggerDispatch(this);
                return true;
            } finally {
                releaseLock();
            }
        }


        @Override
        public final void close() {
            acquireLock();
            try {
                actor.messageDispatcher.unRegister(this);
            }
            finally {
                releaseLock();
            }
        }

        private void releaseLock() {
            lock.unlock();
        }

        private void acquireLock() {
            lock.lock();
        }

        private void releaseMessage(String id) {
            acquireLock();
            try {
                inFlight.remove(id);
                messages.remove(id);
            }
            finally {
                releaseLock();
            }
        }
    }

    interface Dispatcher<M extends Message> {
        void register(Mailbox<M> inMailbox);
        void unRegister(Mailbox<M> inMailbox);
        void triggerDispatch(Mailbox<M> inMailbox);
        boolean isRunning();
        void close();

        //Always executed inside mailbox lock
        default void dispatch(final Mailbox<M> mailbox) {
            //Find new messages
            val newInOrderedMessages = mailbox.messages.keySet()
                    .stream()
                    .limit(mailbox.maxConcurrency)
                    .collect(Collectors.toSet());
            val newMessageIds = Set.copyOf(Sets.difference(newInOrderedMessages, mailbox.inFlight));
            if (newMessageIds.isEmpty()) {
                if(mailbox.inFlight.size() == mailbox.maxConcurrency) {
                    log.warn("Reached max concurrency:{}. Ignoring consumption till inflight messages are consumed",
                            mailbox.maxConcurrency);
                }
                else {
                    log.debug("No new messages. Neither is actor stopped. Ignoring spurious dispatch.");
                }
                return;
            }
            mailbox.inFlight.addAll(newMessageIds);
            val messagesToBeDelivered = newMessageIds.stream()
                    .map(mailbox.messages::get)
                    .toList();
            messagesToBeDelivered.forEach(internalMessage -> mailbox.actor.executorService.submit(() -> {
                val id = internalMessage.getId();
                try {
                    mailbox.actor.processWithObserver(internalMessage);
                }
                catch (Throwable throwable) {
                    log.error("Error processing internalMessage", throwable);
                }
                finally {
                    mailbox.releaseMessage(id);
                }
            }));
        }
    }

    private static class SyncDispatcher<M extends Message> implements Dispatcher<M> {

        private final Map<Integer, Mailbox<M>> registeredMailbox;

        public SyncDispatcher(int partition){
            registeredMailbox = new HashMap<>(partition);
        }

        @Override
        public void register(final Mailbox<M> inMailbox) {
            registeredMailbox.putIfAbsent(inMailbox.partition, inMailbox);
        }

        @Override
        public void unRegister(final Mailbox<M> inMailbox) {
            registeredMailbox.remove(inMailbox.partition);
        }

        @Override
        public void triggerDispatch(final Mailbox<M> inMailbox) {
            //Sync dispatch is executed within mailbox lock
            dispatch(inMailbox);
        }

        @Override
        public boolean isRunning() {
            return !registeredMailbox.isEmpty();
        }

        @Override
        public void close() {
        }
    }

    private static class AsyncIsolatedThreadpoolDispatcher<M extends Message> implements Dispatcher<M> {
        private final ExecutorService executorService;
        private final Map<Integer, AsyncDispatcherWorker<M>> registeredMailboxWorker;

        public AsyncIsolatedThreadpoolDispatcher(int inPartitions) {
            this.executorService = Executors.newFixedThreadPool(inPartitions);
            this.registeredMailboxWorker = new HashMap<>(inPartitions);
        }

        @Override
        public final void register(final Mailbox<M> inMailbox) {
            val mailBoxAsyncDispatcherWorker = new AsyncDispatcherWorker<>(inMailbox, this);
            registeredMailboxWorker.putIfAbsent(inMailbox.partition, mailBoxAsyncDispatcherWorker);
            mailBoxAsyncDispatcherWorker.start(executorService::submit);
        }

        @Override
        public final void unRegister(final Mailbox<M> inMailbox) {
            if(registeredMailboxWorker.containsKey(inMailbox.partition)) {
                registeredMailboxWorker.get(inMailbox.partition).close();
                registeredMailboxWorker.remove(inMailbox.partition);
            }
        }

        @Override
        public final void triggerDispatch(final Mailbox<M> inMailbox) {
            registeredMailboxWorker.get(inMailbox.partition).trigger();
        }

        @Override
        public final boolean isRunning() {
            return !registeredMailboxWorker.isEmpty()
                    && registeredMailboxWorker.values()
                    .stream()
                    .allMatch(AsyncDispatcherWorker::isRunning);
        }


        @Override
        public final void close() {
            executorService.shutdown();
        }

        public static class AsyncDispatcherWorker<M extends Message>  implements AutoCloseable {
            private final Mailbox<M> mailbox;
            private final AtomicBoolean stopped;
            private final Condition checkCondition;
            private Future<?> monitoredFuture;

            public AsyncDispatcherWorker(Mailbox<M> inMailbox, Dispatcher<M> inDispatcher) {
                mailbox = inMailbox;
                stopped = new AtomicBoolean(false);
                checkCondition = inMailbox.lock.newCondition();
            }

            public final void start(final Function<Runnable, Future<?>> taskSubmitter) {
                monitoredFuture = taskSubmitter.apply(this::monitor);
            }

            public final void close() {
                stopped.set(true);
                checkCondition.signalAll();
                monitoredFuture.cancel(true);
            }

            public final void trigger() {
                checkCondition.signalAll();
            }

            public final boolean isRunning() {
                return !stopped.get();
            }

            private void monitor() {
                val name = mailbox.name;
                mailbox.acquireLock();
                try {
                    while (true) {
                        //We can do the tests twice or just stop
                        //waiting after sometime and check the conditions anyway
                        //The set difference operation _might_ be expensive, hence going for the latter approach for now
                        //Can be changed in the future if needed
                        checkCondition.await(100, TimeUnit.MILLISECONDS);
                        if (stopped.get()) {
                            log.info("Actor {} monitor thread exiting", name);
                            return;
                        }
                        mailbox.actor.messageDispatcher.dispatch(mailbox);
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
    }

}

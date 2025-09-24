package io.appform.memq.actor;

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
        this.name = name;
        this.executorService = executorService;
        this.validationHandler = validationHandler;
        this.consumerHandler = consumerHandler;
        this.sidelineHandler = sidelineHandler;
        this.exceptionHandler = exceptionHandler;
        this.retryer = retryer;
        this.partitioner = partitioner;
        this.messageDispatcher = new SyncDispatcher<M>();
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

    private static class Mailbox<M extends Message> implements AutoCloseable {

        private final Actor<M> actor;
        private final String name;
        private final long maxSize;
        private final int maxConcurrency;
        private final ReentrantLock lock = new ReentrantLock();
        private final Map<String, InternalMessage<M>> messages = new LinkedHashMap<>();
        private final Set<String> inFlight = new HashSet<>();
        private final AtomicBoolean stopped = new AtomicBoolean();

        public Mailbox(Actor<M> actor, int partition, long maxSize, int maxConcurrency) {
            this.actor = actor;
            this.maxSize = maxSize;
            this.maxConcurrency = maxConcurrency;
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
                return actor.messageDispatcher.triggerDispatch(this);
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

        void releaseLock() {
            lock.unlock();
        }

        void acquireLock() {
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
        boolean triggerDispatch(Mailbox<M> inMailbox);
        boolean isRunning();
        void close();

        default boolean dispatch(Mailbox<M> mailbox) {
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
                    return false;
                }
                else {
                    log.debug("No new messages. Neither is actor stopped. Ignoring spurious dispatch.");
                }
                return true;
            }
            mailbox.inFlight.addAll(newMessageIds);
            val messagesToBeDelivered = newMessageIds.stream()
                    .map(mailbox.messages::get)
                    .toList();
            messagesToBeDelivered.forEach(internalMessage -> mailbox.actor.executorService.submit(() -> {
                val id = internalMessage.getId();
                try {
                    val observerMessageMeta = new ObserverMessageMeta(id, internalMessage.getPublishedAt(),
                            internalMessage.getValidTill());
                    mailbox.actor.rootObserver.execute(ActorObserverContext.builder()
                                    .messageMeta(observerMessageMeta)
                                    .message(internalMessage.getMessage())
                                    .operation(ActorOperation.CONSUME)
                                    .actorName(mailbox.actor.name)
                                    .build(),
                            () -> process(mailbox.actor, internalMessage));
                }
                catch (Throwable throwable) {
                    log.error("Error processing internalMessage", throwable);
                }
                finally {
                    mailbox.releaseMessage(id);
                }
            }));
            return true;
        }

        default boolean process(final Actor<M> actor, final InternalMessage<M> internalMessage) {
            val id = internalMessage.getId();
            val message = internalMessage.getMessage();
            var status = false;
            var messageMeta = new MessageMeta(internalMessage.getPublishedAt(),
                    internalMessage.getValidTill(),
                    internalMessage.getHeaders());
            try {
                val valid = actor.rootObserver.execute(ActorObserverContext.builder()
                                .message(message)
                                .operation(ActorOperation.VALIDATE)
                                .actorName(actor.name)
                                .build(),
                        () -> actor.validationHandler.apply(message, messageMeta));
                if (!valid) {
                    log.debug("Message validation failed for message: {}", message);
                    return false;
                }
                else {
                    status = actor.retryer.execute(() -> {
                        messageMeta.incrementAttempt();
                        return actor.consumerHandler.apply(message, messageMeta);
                    });
                    if (!status) {
                        log.debug("Consumer failed for message: {}", message);
                        actor.rootObserver.execute(ActorObserverContext.builder()
                                        .message(message)
                                        .operation(ActorOperation.SIDELINE)
                                        .actorName(actor.name)
                                        .build(),
                                () -> {
                                    actor.sidelineHandler.accept(message, messageMeta);
                                    return true;
                                });
                    }
                }
            } catch (Exception e) {
                log.error("Error processing message : " + id, e);
                actor.rootObserver.execute(ActorObserverContext.builder()
                                .message(message)
                                .operation(ActorOperation.HANDLE_EXCEPTION)
                                .actorName(actor.name)
                                .build(),
                        () -> {
                            actor.exceptionHandler.accept(message, messageMeta, e);
                            return true;
                        });
            }
            return status;
        }
    }


    public static class SyncDispatcher<M extends Message> implements Dispatcher<M> {

        private final Map<String, Mailbox<M>> registeredMailbox;

        SyncDispatcher(){
            registeredMailbox = new ConcurrentHashMap<>();
        }

        @Override
        public void register(Mailbox<M> inMailbox) {
            registeredMailbox.putIfAbsent(inMailbox.name, inMailbox);
        }

        @Override
        public void unRegister(Mailbox<M> inMailbox) {
            log.info("Unregistering dispatcher for actor:{} mailbox:{}", inMailbox.actor.name, inMailbox.name);
            registeredMailbox.remove(inMailbox.name);
        }

        @Override
        public boolean triggerDispatch(Mailbox<M> inMailbox) {
            return dispatch(inMailbox);
        }

        @Override
        public boolean isRunning() {
            return !registeredMailbox.isEmpty();
        }

        @Override
        public void close() {
        }
    }

    public static class AsyncIsolatedThreadpoolDispatcher<M extends Message> implements Dispatcher<M> {
        private final ExecutorService executorService;
        private final Map<String, Mailbox<M>> registeredMailbox;
        private final Map<String, Condition> registeredConditions;
        private final Map<String, AtomicBoolean> mailboxStopped;
        private final List<Future<?>> monitoredFutures;

        AsyncIsolatedThreadpoolDispatcher(int inPartitions) {
            this.executorService = Executors.newFixedThreadPool(inPartitions);
            this.registeredMailbox = new ConcurrentHashMap<>();
            this.registeredConditions = new ConcurrentHashMap<>();
            this.mailboxStopped = new ConcurrentHashMap<>();
            this.monitoredFutures = new LinkedList<>();
        }


        @Override
        public void register(Mailbox<M> inMailbox) {
            registeredMailbox.putIfAbsent(inMailbox.name, inMailbox);
            val condition = inMailbox.lock.newCondition();
            registeredConditions.put(inMailbox.name, condition);
            mailboxStopped.put(inMailbox.name, new AtomicBoolean(false));
            monitoredFutures.add(executorService.submit(()-> monitor(inMailbox, condition)));

        }

        @Override
        public void unRegister(Mailbox<M> inMailbox) {
            log.info("Unregistering dispatcher for actor:{} mailbox:{}", inMailbox.actor.name, inMailbox.name);
            registeredMailbox.remove(inMailbox.name);
            if(mailboxStopped.containsKey(inMailbox.name)) {
                mailboxStopped.get(inMailbox.name).set(true);
            }
        }

        @Override
        public boolean triggerDispatch(Mailbox<M> inMailbox) {
            registeredConditions.get(inMailbox.name).signalAll(); //TODO::can do better from worker?
            return true;
        }

        @Override
        public boolean isRunning() {
            return mailboxStopped.values()
                    .stream()
                    .allMatch(stopped -> !stopped.get());
        }


        @Override
        public void close() {
            monitoredFutures.forEach(monitoredFuture -> monitoredFuture.cancel(true));
            executorService.shutdown();
        }

        private void monitor(Mailbox<M> mailbox, Condition checkCondition) {
            val name = mailbox.name;
            mailbox.acquireLock();
            try {
                while (true) {
                    //We can do the tests twice or just stop
                    //waiting after sometime and check the conditions anyway
                    //The set difference operation _might_ be expensive, hence going for the latter approach for now
                    //Can be changed in the future if needed
                    checkCondition.await(100, TimeUnit.MILLISECONDS);
                    if (mailboxStopped.get(mailbox.name).get()) {
                        log.info("Actor {} monitor thread exiting", name);
                        return;
                    }
                    dispatch(mailbox);
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

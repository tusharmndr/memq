package io.appform.memq.actor;

import com.google.common.collect.Sets;
import io.appform.memq.mailbox.config.BoundedMailboxConfig;
import io.appform.memq.mailbox.config.MailboxConfig;
import io.appform.memq.mailbox.config.MailboxConfigVisitor;
import io.appform.memq.mailbox.config.UnBoundedMailboxConfig;
import io.appform.memq.observer.ActorObserver;
import io.appform.memq.observer.ActorObserverContext;
import io.appform.memq.observer.TerminalActorObserver;
import io.appform.memq.retry.RetryStrategy;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import lombok.val;

import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
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
    private final Map<Integer, Mailbox<M>> mailboxes;
    private final Function<M, Boolean> validationHandler;
    private final Function<M, Boolean> consumerHandler;
    private final Consumer<M> sidelineHandler;
    private final BiConsumer<M, Throwable> exceptionHandler;
    private final RetryStrategy retryer;
    private ActorObserver rootObserver;


    @SneakyThrows
    public Actor(
            String name,
            ExecutorService executorService,
            Function<M, Boolean> validationHandler,
            Function<M, Boolean> consumerHandler,
            Consumer<M> sidelineHandler,
            BiConsumer<M, Throwable> exceptionHandler,
            RetryStrategy retryer,
            int partitions,
            ToIntFunction<M> partitioner,
            MailboxConfig mailboxConfig,
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
        this.messageDispatcher = Executors.newFixedThreadPool(partitions);
        this.retryer = retryer;
        this.partitioner = partitioner;
        this.mailboxes = IntStream.range(0, partitions)
                .boxed()
                .collect(Collectors.toMap(Function.identity(), i -> mailbox(this, mailboxConfig, i)));
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

    public final boolean isRunning() {
        return mailboxes.values()
                .stream()
                .allMatch(Mailbox::isRunning);
    }

    public final boolean publish(final M message) {
        return rootObserver.execute(ActorObserverContext.builder()
                        .operation(ActorOperation.PUBLISH)
                        .message(message)
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


    private Mailbox<M> mailbox(Actor<M> actor, MailboxConfig mailboxConfig, Integer i) {
        return mailboxConfig.accept(new MailboxConfigVisitor<>() {
            @Override
            public Mailbox<M> visit(BoundedMailboxConfig boundedMailboxConfig) {
                return new BoundedMailbox<>(actor, i, boundedMailboxConfig.getMaxSize());
            }

            @Override
            public Mailbox<M> visit(UnBoundedMailboxConfig unBoundedMailboxConfig) {
                return new UnboundedMailbox<>(actor, i);
            }
        });
    }

    static class UnboundedMailbox<M extends Message> extends Mailbox<M> {
        public UnboundedMailbox(Actor<M> actor, int partition) {
            super(actor, partition);
        }

        @Override
        public final boolean publish(final M message) {
            lock.lock();
            try {
                val messageId = message.id();
                messages.putIfAbsent(messageId, message);
                checkCondition.signalAll();
            } finally {
                lock.unlock();
            }
            return true;
        }

    }

    static class BoundedMailbox<M extends Message> extends Mailbox<M> {
        private final long maxSize;
        public BoundedMailbox(Actor<M> actor, int partition, long maxSize) {
            super(actor, partition);
            this.maxSize = maxSize;
        }

        @Override
        public final boolean publish(final M message) {
            lock.lock();
            try {
                val currSize = messages.size();
                if (currSize >= this.maxSize) {
                    log.warn("Blocking publish for as curr size:{} is more than specified threshold:{}",
                            currSize, this.maxSize);
                    return false;
                }
                val messageId = message.id();
                messages.putIfAbsent(messageId, message);
                checkCondition.signalAll();
            } finally {
                lock.unlock();
            }
            return true;
        }
    }


    private abstract static class Mailbox<M extends Message> implements AutoCloseable {

        protected final ReentrantLock lock = new ReentrantLock();
        protected final Map<String, M> messages = new HashMap<>();
        protected final Condition checkCondition = lock.newCondition();
        private final Actor<M> actor;
        private final String name;
        private final Set<String> inFlight = new HashSet<>();
        private final AtomicBoolean stopped = new AtomicBoolean();
        private Future<?> monitorFuture;

        protected Mailbox(Actor<M> actor, int partition) {
            this.actor = actor;
            this.name = actor.name + "-" + partition;
        }

        public final boolean isEmpty() {
            lock.lock();
            try {
                return messages.isEmpty();
            }
            finally {
                lock.unlock();
            }
        }

        public final long size() {
            lock.lock();
            try {
                return messages.size();
            } finally {
                lock.unlock();
            }
        }

        public final boolean isRunning() {
            return !stopped.get();
        }

        public final void start() {
            monitorFuture = actor.messageDispatcher.submit(this::monitor);
        }

        public abstract boolean publish(final M message);

        @Override
        public final void close() {
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
            actor.messageDispatcher.shutdown();
        }

        private void monitor() {
            lock.lock();
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
                    //Find new messages
                    val newMessageIds = Set.copyOf(Sets.difference(messages.keySet(), inFlight));
                    if (newMessageIds.isEmpty()) {
                        log.debug("No new messages. Neither is actor stopped. Ignoring spurious wakeup.");
                        continue;
                    }
                    inFlight.addAll(newMessageIds);
                    val messagesToBeDelivered = newMessageIds.stream()
                                    .map(messages::get)
                                            .toList();
                    messagesToBeDelivered.forEach(message -> actor.executorService.submit(() -> {
                        try {
                            actor.rootObserver.execute(ActorObserverContext.builder()
                                                               .message(message)
                                                               .operation(ActorOperation.CONSUME)
                                                               .build(),
                                                       () -> process(message));
                        }
                        catch (Throwable throwable) {
                            log.error("Error processing message", throwable);
                        }
                        finally {
                            releaseMessage(message.id());
                        }
                    }));
                }
            }
            catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.warn("Monitor thread stopped for {}", name);
            }
            finally {
                lock.unlock();
            }
        }

        private boolean process(final M message) {
            val id = message.id();
            var status = false;
            try {
                val valid = actor.rootObserver.execute(ActorObserverContext.builder()
                                .message(message)
                                .operation(ActorOperation.VALIDATE)
                                .build(),
                        () -> actor.validationHandler.apply(message));

                if (!valid) {
                    log.debug("Message validation failed for message: {}", message);
                }
                else {
                    status = actor.retryer.execute(() -> actor.consumerHandler.apply(message));
                    if (!status) {
                        log.debug("Consumer failed for message: {}", message);
                        actor.rootObserver.execute(ActorObserverContext.builder()
                                        .message(message)
                                        .operation(ActorOperation.SIDELINE)
                                        .build(),
                                () -> {
                                    actor.sidelineHandler.accept(message);
                                    return true;
                                });
                    }
                }
            }
            catch (Exception e) {
                log.error("Error processing message : " + id, e);
                actor.rootObserver.execute(ActorObserverContext.builder()
                                                          .message(message)
                                                          .operation(ActorOperation.HANDLE_EXCEPTION)
                                                   .build(),
                                           () -> {
                                            actor.exceptionHandler.accept(message, e);
                                            return true;
                                           });
            }
            return status;
        }

        private void releaseMessage(String id) {
            lock.lock();
            try {
                inFlight.remove(id);
                messages.remove(id);
            }
            finally {
                lock.unlock();
            }
        }
    }
}

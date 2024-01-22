package io.appform.memq.actor;

import com.google.common.collect.Sets;
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
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.ToIntFunction;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Slf4j
public class Actor<M extends Message> implements AutoCloseable {

    private final String name;
    private final ExecutorService executorService;
    private final ExecutorService messageDispatcher; //TODO::Separate dispatch and add NoDispatch flow
    private final ToIntFunction<M> partitioner;
    private final Map<Integer, UnboundedMailbox<M>> mailboxes;
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
                .collect(Collectors.toMap(Function.identity(), i -> new UnboundedMailbox<M>(this, i)));
        this.rootObserver = setupObserver(observers);
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

    public final void publish(final M message) {
        rootObserver.execute(ActorObserverContext.builder()
                                     .operation(ActorOperation.PUBLISH)
                                     .build(),
                             () -> mailboxes.get(partitioner.applyAsInt(message))
                                     .publish(message));
    }

    public final void start() {
        mailboxes.values().forEach(UnboundedMailbox::start);
    }

    @Override
    public final void close() {
        mailboxes.values().forEach(UnboundedMailbox::close);
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
                val empty= messages.isEmpty();
                checkCondition.signalAll(); //TODO: Check if needed
                return empty;
            }
            finally {
                lock.unlock();
            }
        }

        public final long size() {
            lock.lock();
            try {
                val size = messages.size();
                checkCondition.signalAll(); //TODO: Check if needed
                return size;
            } finally {
                lock.unlock();
            }
        }

        public final void start() {
            monitorFuture = actor.messageDispatcher.submit(this::monitor);
        }

        public final void publish(final M message) {
            lock.lock();
            try {
                val messageId = message.id();
                messages.putIfAbsent(messageId, message);
                checkCondition.signalAll();
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
                            actor.rootObserver.execute(ActorObserverContext.builder()
                                                               .operation(ActorOperation.CONSUME)
                                                               .build(),
                                                       () -> this.process(messages.get(m)));
                        }
                        catch (Throwable throwable) {
                            log.error("Error", throwable);
                        }
                    }));
                }
            }
            catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.warn("Monitor thread interrupted for {}", name);
            }
            finally {
                lock.unlock();
            }
        }

        private Void process(final M message) {
            val id = message.id();
            try {
                boolean valid = actor.validationHandler.apply(message);
                if (!valid) {
                    log.debug("Message validation failed for message: {}", message);
                }
                else {
                    val status = actor.retryer.execute(() -> actor.consumerHandler.apply(message));
                    if (!status) {
                        log.debug("Consumer failed for message: {}", message);
                        actor.rootObserver.execute(ActorObserverContext.builder()
                                                           .operation(ActorOperation.SIDELINE)
                                                           .build(),
                                                   () -> actor.sidelineHandler.accept(message));
                    }
                }
            }
            catch (Exception e) {
                actor.rootObserver.execute(ActorObserverContext.builder()
                                                   .operation(ActorOperation.HANDLE_EXCEPTION)
                                                   .build(),
                                           () -> actor.exceptionHandler.accept(message, e));
            }
            finally {
                releaseMessage(id);
            }
            return null;
        }

        private void releaseMessage(String id) {
            lock.lock();
            try {
                inFlight.remove(id);
                messages.remove(id);
                checkCondition.signalAll(); //Redeliver
            }
            finally {
                lock.unlock();
            }
        }
    }
}

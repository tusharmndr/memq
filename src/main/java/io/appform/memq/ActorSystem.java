package io.appform.memq;

import io.appform.memq.actor.*;
import io.appform.memq.exceptionhandler.config.DropConfig;
import io.appform.memq.exceptionhandler.config.ExceptionHandlerConfig;
import io.appform.memq.exceptionhandler.config.ExceptionHandlerConfigVisitor;
import io.appform.memq.exceptionhandler.config.SidelineConfig;
import io.appform.memq.retry.RetryStrategy;
import io.appform.memq.retry.RetryStrategyFactory;
import lombok.extern.slf4j.Slf4j;
import lombok.val;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.*;
import java.util.stream.Collectors;

@Slf4j
public class ActorSystem implements AutoCloseable {

    private final ConcurrentHashMap<String, ExecutorService> executors;
    private final ExecutorServiceProvider executorServiceProvider;
    private final Map<String, ExecutorConfig> executorConfigMap;
    private final List<Actor<?>> registeredActors;
    private final RetryStrategyFactory retryStrategyFactory;

    public ActorSystem(ActorSystemConfig config) {
        this(config, (name, parallel) -> Executors.newFixedThreadPool(parallel));
    }

    public ActorSystem(ActorSystemConfig config,
                       ExecutorServiceProvider executorServiceProvider) {
        this.executorServiceProvider = executorServiceProvider;
        this.executorConfigMap = config.getExecutorConfig().stream()
                .collect(Collectors.toMap(ExecutorConfig::getName, Function.identity()));
        this.executors = new ConcurrentHashMap<>();
        this.registeredActors = new ArrayList<>();
        this.retryStrategyFactory = new RetryStrategyFactory();
    }
    //System shutdown
    @Override
    public void close() {
        registeredActors.forEach(Actor::close);
        executors.values().forEach(ExecutorService::shutdown);
    }

    public final void register(Actor<?> actor) {
        registeredActors.add(actor);
    }

    public final ExecutorService createOrGetExecutorService(String name) {
        val threadPoolSize = determineThreadPoolSize(name);
        return executors.computeIfAbsent(name, executor -> executorServiceProvider.threadPool(name, threadPoolSize));
    }

    public RetryStrategy createRetryer(ActorConfig actorConfig) {
        return retryStrategyFactory.create(actorConfig.getRetryConfig());
    }

    public  <M extends Message> BiConsumer<M, Throwable> createExceptionHandler(ActorConfig actorConfig,
                                                                                     Consumer<M> sidelineHandler) {
        ExceptionHandlerConfig exceptionHandlerConfig = actorConfig.getExceptionHandlerConfig();
        return exceptionHandlerConfig.accept(new ExceptionHandlerConfigVisitor<>() {
            @Override
            public BiConsumer<M, Throwable> visit(DropConfig config) {
                return (message, throwable) -> {};
            }

            @Override
            public BiConsumer<M, Throwable> visit(SidelineConfig config) {
                return (message, throwable) -> sidelineHandler.accept(message);
            }
        });
    }

    public <M extends Message> ToIntFunction<M> partitioner(ActorConfig actorConfig,
                                                            ToIntFunction<M> partitioner) {
        return partitioner != null ? partitioner
                : actorConfig.getPartitions() == 1 ? message -> 0
                : message -> Math.absExact(message.id().hashCode()) % actorConfig.getPartitions();
    }

    private int determineThreadPoolSize(String name) {
        return executorConfigMap.getOrDefault(name, new ExecutorConfig(name, 10))
                .getThreadPoolSize();
    }
}

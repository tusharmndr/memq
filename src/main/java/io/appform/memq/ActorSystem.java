package io.appform.memq;

import io.appform.memq.actor.Actor;
import io.appform.memq.exceptionhandler.ExceptionHandlingFactory;
import io.appform.memq.retry.RetryStrategyFactory;
import lombok.val;

import java.io.Closeable;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.function.Function;
import java.util.stream.Collectors;

public class ActorSystem implements Closeable {

    private final ConcurrentHashMap<String, ExecutorService> executors;
    private final ExecutorServiceProvider executorServiceProvider;
    private final Map<String, ExecutorConfig> executorConfigMap;
    private final List<Actor<?>> registeredActors;
    private final RetryStrategyFactory retryStrategyFactory;
    private final ExceptionHandlingFactory exceptionHandlingFactory;

    public ActorSystem(ActorSystemConfig config,
                       ExecutorServiceProvider executorServiceProvider) {
        this.executorServiceProvider = executorServiceProvider;
        this.executorConfigMap = config.getExecutorConfig().stream()
                .collect(Collectors.toMap(ExecutorConfig::getName, Function.identity()));
        this.executors = new ConcurrentHashMap<>();
        this.registeredActors = new ArrayList<>();
        this.retryStrategyFactory = new RetryStrategyFactory();
        this.exceptionHandlingFactory = new ExceptionHandlingFactory();
    }

    public final ExecutorService createOrGetExecutorService(String name) {
        val threadPoolSize = determineThreadPoolSize(name);
        return executors.computeIfAbsent(name, executor -> executorServiceProvider.newFixedThreadPool(name, threadPoolSize));
    }

    public final void register(Actor<?> actor) {
        registeredActors.add(actor);
    }

    public RetryStrategyFactory retryStrategyFactory() {
        return retryStrategyFactory;
    }

    public ExceptionHandlingFactory exceptionHandlingFactory() {
        return exceptionHandlingFactory;
    }


    //System shutdown
    @Override
    public void close() {
        registeredActors.forEach(Actor::close);
        executors.values().forEach(ExecutorService::shutdown);
    }

    private int determineThreadPoolSize(String name) {
        return executorConfigMap.getOrDefault(name, new ExecutorConfig(name, 10)).getThreadPoolSize();
    }

}

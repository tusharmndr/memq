package io.appform;

import io.appform.config.ExecutorConfig;
import io.appform.config.MemqConfig;
import io.appform.memq.ActorSystem;
import io.appform.memq.actor.Actor;
import io.appform.memq.actor.ActorConfig;
import io.appform.memq.retry.RetryStrategy;
import io.appform.memq.retry.RetryStrategyFactory;
import io.dropwizard.lifecycle.Managed;
import lombok.extern.slf4j.Slf4j;
import lombok.val;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.*;
import java.util.stream.Collectors;

@Slf4j
public class MemqActorSystem implements ActorSystem, Managed {

    private final ConcurrentHashMap<String, ExecutorService> executors;
    private final ExecutorServiceProvider executorServiceProvider;
    private final Map<String, ExecutorConfig> executorConfigMap;
    private final List<Actor<?>> registeredActors;
    private final RetryStrategyFactory retryStrategyFactory;

    public MemqActorSystem(MemqConfig memqConfig) {
        this(memqConfig, (name, parallel) -> Executors.newFixedThreadPool(parallel));
    }

    public MemqActorSystem(MemqConfig memqConfig,
                           ExecutorServiceProvider executorServiceProvider) {
        this.executorServiceProvider = executorServiceProvider;
        this.executorConfigMap = memqConfig.getExecutors().stream()
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

    @Override
    public final void register(Actor<?> actor) {
        registeredActors.add(actor);
    }

    @Override
    public final ExecutorService createOrGetExecutorService(ActorConfig config) {
        val name = config.getExecutorName();
        val threadPoolSize = determineThreadPoolSize(name);
        return executors.computeIfAbsent(name, executor -> executorServiceProvider.threadPool(name, threadPoolSize));
    }

    @Override
    public final RetryStrategy createRetryer(ActorConfig actorConfig) {
        return retryStrategyFactory.create(actorConfig.getRetryConfig());
    }

    private int determineThreadPoolSize(String name) {
        return executorConfigMap.getOrDefault(name, new ExecutorConfig(name, 10))
                .getThreadPoolSize();
    }

    @Override
    public void start() {
        log.info("Started Memq Actor System");
    }

    @Override
    public void stop() {
        this.close();
        log.info("Closed Memq Actor System");
    }
}

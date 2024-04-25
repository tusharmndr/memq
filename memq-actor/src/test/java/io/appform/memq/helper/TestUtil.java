package io.appform.memq.helper;

import com.codahale.metrics.MetricRegistry;
import com.google.common.collect.Lists;
import io.appform.memq.ActorSystem;
import io.appform.memq.HighLevelActor;
import io.appform.memq.actor.Actor;
import io.appform.memq.actor.HighLevelActorConfig;
import io.appform.memq.exceptionhandler.config.ExceptionHandlerConfig;
import io.appform.memq.exceptionhandler.config.SidelineConfig;
import io.appform.memq.helper.message.TestIntMessage;
import io.appform.memq.retry.RetryStrategy;
import io.appform.memq.retry.RetryStrategyFactory;
import io.appform.memq.retry.config.NoRetryConfig;
import lombok.val;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class TestUtil {

    public enum HighLevelActorType {
        EXCEPTION_ACTOR,
    }

    public static final String GLOBAL_EXECUTOR_SERVICE_GROUP = "global";
    public static final int DEFAULT_THREADPOOL_SIZE = 2;

    public static ActorSystem actorSystem(ExecutorService tp) {
        val metricRegistry = new MetricRegistry();
        return new ActorSystem() {
            private final RetryStrategyFactory retryStrategyFactory = new RetryStrategyFactory();
            private final List<Actor<?>> registeredActors = Lists.newArrayList();

            @Override
            public void register(Actor<?> actor) {
                registeredActors.add(actor);
                actor.start();
            }

            @Override
            public ExecutorService createOrGetExecutorService(HighLevelActorConfig config) {
                return tp;
            }

            @Override
            public RetryStrategy createRetryer(HighLevelActorConfig highLevelActorConfig) {
                return retryStrategyFactory.create(highLevelActorConfig.getRetryConfig());
            }

            @Override
            public MetricRegistry metricRegistry() {
                return metricRegistry ;
            }

            @Override
            public boolean isRunning() {
                return !registeredActors.isEmpty() && registeredActors.stream().allMatch(Actor::isRunning);
            }

            @Override
            public void close() {
                registeredActors.forEach(Actor::close);
            }
        };
    }

    public static HighLevelActor<HighLevelActorType,TestIntMessage> allExceptionActor(final AtomicInteger counter,
                                     final AtomicBoolean sideline,
                                     final HighLevelActorConfig highLevelActorConfig,
                                     final ActorSystem actorSystem) {
        return new HighLevelActor<>(HighLevelActorType.EXCEPTION_ACTOR,
                highLevelActorConfig,
                actorSystem,
                null,
                List.of()
        ) {
            @Override
            protected boolean handle(TestIntMessage message) {
                counter.addAndGet(message.getValue());
                throw new RuntimeException();
            }

            @Override
            protected void sideline(TestIntMessage message) {
                sideline.set(true);
            }
        };
    }

    public static HighLevelActorConfig noRetryActorConfig(int partition, ExceptionHandlerConfig exceptionHandlerConfig) {
        return noRetryActorConfig(partition, false, exceptionHandlerConfig);
    }

    public static HighLevelActorConfig noRetryActorConfig(int partition, boolean metricDisabled) {
        return noRetryActorConfig(partition, metricDisabled, new SidelineConfig());
    }

    public static HighLevelActorConfig noRetryActorConfig(int partition) {
        return noRetryActorConfig(partition, false, new SidelineConfig());
    }

    public static HighLevelActorConfig noRetryActorConfig(int partition,
                                                          boolean metricDisabled,
                                                          ExceptionHandlerConfig exceptionHandlerConfig) {
        return HighLevelActorConfig.builder()
                .partitions(partition)
                .retryConfig(new NoRetryConfig())
                .executorName(GLOBAL_EXECUTOR_SERVICE_GROUP)
                .metricDisabled(metricDisabled)
                .exceptionHandlerConfig(exceptionHandlerConfig)
                .build();
    }
}

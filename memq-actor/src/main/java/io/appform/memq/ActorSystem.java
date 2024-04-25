package io.appform.memq;

import com.codahale.metrics.MetricRegistry;
import io.appform.memq.actor.Actor;
import io.appform.memq.actor.HighLevelActorConfig;
import io.appform.memq.actor.Message;
import io.appform.memq.exceptionhandler.config.DropConfig;
import io.appform.memq.exceptionhandler.config.ExceptionHandlerConfigVisitor;
import io.appform.memq.exceptionhandler.config.SidelineConfig;
import io.appform.memq.observer.ActorObserver;
import io.appform.memq.retry.RetryStrategy;
import io.appform.memq.stats.ActorMetricObserver;
import lombok.val;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.ToIntFunction;

public interface ActorSystem extends AutoCloseable {

    void register(Actor<?> actor);

    ExecutorService createOrGetExecutorService(HighLevelActorConfig config);

    RetryStrategy createRetryer(HighLevelActorConfig highLevelActorConfig);

    MetricRegistry metricRegistry();

    boolean isRunning();

    default List<ActorObserver> observers(String name, HighLevelActorConfig config, List<ActorObserver> observers) {
        val updatedObservers = new ArrayList<ActorObserver>();

        if (observers != null) {
            updatedObservers.addAll(observers);
        }

        if (!config.isMetricDisabled()) {
            updatedObservers.add(new ActorMetricObserver(name, metricRegistry()));
        }

        return updatedObservers;
    }

    default <M extends Message> Function<M, Boolean> expiryValidator(HighLevelActorConfig highLevelActorConfig) {
        return message -> message.validTill() > System.currentTimeMillis();
    }

    default <M extends Message> BiConsumer<M, Throwable> createExceptionHandler(
            HighLevelActorConfig highLevelActorConfig,
            Consumer<M> sidelineHandler) {
        val exceptionHandlerConfig = highLevelActorConfig.getExceptionHandlerConfig();
        return exceptionHandlerConfig.accept(new ExceptionHandlerConfigVisitor<>() {
            @Override
            public BiConsumer<M, Throwable> visit(DropConfig config) {
                return (message, throwable) -> {
                };
            }

            @Override
            public BiConsumer<M, Throwable> visit(SidelineConfig config) {
                return (message, throwable) -> sidelineHandler.accept(message);
            }
        });
    }

    default <M extends Message> ToIntFunction<M> partitioner(
            HighLevelActorConfig highLevelActorConfig,
            ToIntFunction<M> partitioner) {
        return partitioner != null ? partitioner
                                   : highLevelActorConfig.getPartitions() == Constants.SINGLE_PARTITION
                                     ? message -> Constants.DEFAULT_PARTITION_INDEX
                                     : message -> Math.absExact(message.id().hashCode()) % highLevelActorConfig.getPartitions();
    }

}

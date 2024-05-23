package io.appform.memq;

import com.codahale.metrics.MetricRegistry;
import io.appform.memq.actor.Actor;
import io.appform.memq.actor.Message;
import io.appform.memq.exceptionhandler.config.DropConfig;
import io.appform.memq.exceptionhandler.config.ExceptionHandlerConfigVisitor;
import io.appform.memq.exceptionhandler.config.SidelineConfig;
import io.appform.memq.actor.MessageMeta;
import io.appform.memq.observer.ActorObserver;
import io.appform.memq.retry.RetryStrategy;
import io.appform.memq.stats.ActorMetricObserver;
import io.appform.memq.utils.TriConsumer;
import lombok.val;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.function.*;

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

    default <M extends Message> BiFunction<M, MessageMeta, Boolean> expiryValidator(HighLevelActorConfig highLevelActorConfig) {
        return (message, messageMeta) -> messageMeta.getValidTill() > System.currentTimeMillis();
    }

    default <M extends Message> TriConsumer<M, MessageMeta, Throwable> createExceptionHandler(
            HighLevelActorConfig highLevelActorConfig,
            BiConsumer<M, MessageMeta> sidelineHandler) {
        val exceptionHandlerConfig = highLevelActorConfig.getExceptionHandlerConfig();
        return exceptionHandlerConfig.accept(new ExceptionHandlerConfigVisitor<>() {
            @Override
            public TriConsumer<M, MessageMeta, Throwable> visit(DropConfig config) {
                return (message, messageMeta, throwable) -> {
                };
            }

            @Override
            public TriConsumer<M, MessageMeta, Throwable> visit(SidelineConfig config) {
                return (message, messageMeta,  throwable) -> sidelineHandler.accept(message, messageMeta);
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

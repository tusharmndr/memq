package io.appform.memq;

import com.codahale.metrics.MetricRegistry;
import io.appform.memq.actor.Actor;
import io.appform.memq.actor.ActorConfig;
import io.appform.memq.actor.Message;
import io.appform.memq.exceptionhandler.config.DropConfig;
import io.appform.memq.exceptionhandler.config.ExceptionHandlerConfig;
import io.appform.memq.exceptionhandler.config.ExceptionHandlerConfigVisitor;
import io.appform.memq.exceptionhandler.config.SidelineConfig;
import io.appform.memq.retry.RetryStrategy;

import java.util.concurrent.ExecutorService;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.ToIntFunction;

public interface ActorSystem extends AutoCloseable {

    void register(Actor<?> actor);

    ExecutorService createOrGetExecutorService(ActorConfig config);

    RetryStrategy createRetryer(ActorConfig actorConfig);

    MetricRegistry metricRegistry();

    default <M extends Message> Function<M, Boolean> expiryValidator(ActorConfig actorConfig) {
        return message -> message.validTill() > System.currentTimeMillis();
    }

    default <M extends Message>  BiConsumer<M, Throwable> createExceptionHandler(ActorConfig actorConfig,
                                                             Consumer<M> sidelineHandler) {
        ExceptionHandlerConfig exceptionHandlerConfig = actorConfig.getExceptionHandlerConfig();
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

    default <M extends Message> ToIntFunction<M> partitioner(ActorConfig actorConfig,
                                                             ToIntFunction<M> partitioner) {
        return partitioner != null ? partitioner
                : actorConfig.getPartitions() == 1 ? message -> 0
                : message -> Math.absExact(message.id().hashCode()) % actorConfig.getPartitions();
    }

}

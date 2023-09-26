package io.appform.memq;


import io.appform.memq.actor.Actor;
import io.appform.memq.actor.ActorConfig;
import io.appform.memq.actor.Message;
import io.appform.memq.sideline.SidelineStore;
import io.appform.memq.sideline.impl.NoOpSidelineStore;
import lombok.extern.slf4j.Slf4j;

import java.util.Set;
import java.util.function.ToIntFunction;

@Slf4j
public abstract class HighLevelActor<MessageType extends Enum<MessageType>, M extends Message> extends Actor<M> {

    private final MessageType type;
    private final SidelineStore<M> sidelineStore;

    protected HighLevelActor(MessageType type,
                             ActorConfig actorConfig,
                             ActorSystem actorSystem,
                             Set<Class<? extends Throwable>> ignoredErrors) {
        this(type, actorConfig, actorSystem, ignoredErrors, null, null);
    }

    protected HighLevelActor(MessageType type,
                             ActorConfig actorConfig,
                             ActorSystem actorSystem,
                             Set<Class<? extends Throwable>> ignoredErrors,
                             SidelineStore<M> sidelineStore,
                             ToIntFunction<M> partitioner) {
        super(actorSystem.createOrGetExecutorService(actorConfig.getExecutorConfig().getName()),
                actorSystem.retryStrategyFactory().create(actorConfig.getRetryConfig()),
                actorSystem.exceptionHandlingFactory().create(actorConfig.getExceptionHandlerConfig()),
                ignoredErrors,
                actorConfig.getPartitions(),
                partitioner != null ? partitioner
                        : actorConfig.getPartitions() == 1 ? message -> 0
                            : message -> Math.absExact(message.id().hashCode()) % actorConfig.getPartitions());
        this.type = type;
        this.sidelineStore = sidelineStore == null ? new NoOpSidelineStore<>():  sidelineStore;
        actorSystem.register(this);

    }

    @Override
    public String name() {
        return type.name();
    }

    @Override
    protected void sidelineMessage(final M message) throws Exception {
        sidelineStore.save(message);
    }


}

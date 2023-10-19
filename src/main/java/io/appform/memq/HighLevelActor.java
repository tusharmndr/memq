package io.appform.memq;


import io.appform.memq.actor.*;
import lombok.extern.slf4j.Slf4j;

import java.util.function.ToIntFunction;

@Slf4j
public abstract class HighLevelActor<MessageType extends Enum<MessageType>, M extends Message> {

    private final MessageType type;
    private final Actor<M> actor;
    private final ActorConfig config;

    protected HighLevelActor(MessageType type,
                             ActorConfig actorConfig,
                             ActorSystem actorSystem) {
        this(type, actorConfig, actorSystem, null);
    }

    protected abstract boolean handleMessage(final M message);

    protected abstract void handleSideline(final M message);

    protected HighLevelActor(MessageType type,
                             ActorConfig actorConfig,
                             ActorSystem actorSystem,
                             ToIntFunction<M> partitioner) {
        this.type = type;
        this.config = actorConfig;
        this.actor = new Actor<M>(type.name(),
                actorSystem.createOrGetExecutorService(type.name()),
                message -> true,
                this::handleMessage,
                this::handleSideline,
                actorSystem.createExceptionHandler(actorConfig, this::handleSideline),
                actorSystem.createRetryer(actorConfig),
                actorConfig.getPartitions(),
                actorSystem.partitioner(actorConfig, partitioner));
        actorSystem.register(actor);
    }

}

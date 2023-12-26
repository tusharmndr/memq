package io.appform.memq;


import lombok.extern.slf4j.Slf4j;
import io.appform.memq.actor.Actor;
import io.appform.memq.actor.ActorConfig;
import io.appform.memq.actor.Message;

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
                actorSystem.createOrGetExecutorService(actorConfig),
                actorSystem.expiryValidator(actorConfig),
                this::handleMessage,
                this::handleSideline,
                actorSystem.createExceptionHandler(actorConfig, this::handleSideline),
                actorSystem.createRetryer(actorConfig),
                actorConfig.getPartitions(),
                actorSystem.partitioner(actorConfig, partitioner),
                actorSystem.metricRegistry(),
                actorSystem.observers(actorConfig));
        actorSystem.register(actor);
    }

    public final boolean publish(final M message) {
        return actor.publish(message);
    }

}

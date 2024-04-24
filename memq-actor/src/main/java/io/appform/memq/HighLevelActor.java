package io.appform.memq;


import io.appform.memq.actor.Actor;
import io.appform.memq.actor.Message;
import io.appform.memq.observer.ActorObserver;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.function.ToIntFunction;

@Slf4j
public abstract class HighLevelActor<MessageType extends Enum<MessageType>, M extends Message> {

    @Getter
    private final MessageType type;
    private final Actor<M> actor;

    @SuppressWarnings("unused")
    protected HighLevelActor(
            MessageType type,
            HighLevelActorConfig highLevelActorConfig,
            ActorSystem actorSystem) {
        this(type, highLevelActorConfig, actorSystem, null, List.of());
    }

    protected HighLevelActor(
            MessageType type,
            HighLevelActorConfig highLevelActorConfig,
            ActorSystem actorSystem,
            ToIntFunction<M> partitioner) {
        this(type, highLevelActorConfig, actorSystem, partitioner, List.of());
    }

    protected HighLevelActor(
            MessageType type,
            HighLevelActorConfig highLevelActorConfig,
            ActorSystem actorSystem,
            List<ActorObserver> observers) {
        this(type, highLevelActorConfig, actorSystem, null, observers);
    }

    protected HighLevelActor(
            MessageType type,
            HighLevelActorConfig highLevelActorConfig,
            ActorSystem actorSystem,
            ToIntFunction<M> partitioner,
            List<ActorObserver> observers) {
        this.type = type;
        this.actor = new Actor<>(type.name(),
                                  actorSystem.createOrGetExecutorService(highLevelActorConfig),
                                  actorSystem.expiryValidator(highLevelActorConfig),
                                  this::handle,
                                  this::sideline,
                                  actorSystem.createExceptionHandler(highLevelActorConfig, this::sideline),
                                  actorSystem.createRetryer(highLevelActorConfig),
                                  highLevelActorConfig.getPartitions(),
                                  actorSystem.partitioner(highLevelActorConfig, partitioner),
                                  highLevelActorConfig.getMailboxConfig(),
                                  actorSystem.observers(type.name(), highLevelActorConfig, observers));
        actorSystem.register(actor);
    }

    protected abstract boolean handle(final M message);

    protected void sideline(final M message) {
        log.warn("skipping sideline for actor:{} message:{}", type.name(), message);
    }

    public final boolean publish(final M message) {
        return actor.publish(message);
    }

    public final boolean isEmpty() {
       return actor.isEmpty();
    }

    public final long size() {
       return actor.size();
    }

    public final boolean isRunning() {
        return actor.isRunning();
    }

}

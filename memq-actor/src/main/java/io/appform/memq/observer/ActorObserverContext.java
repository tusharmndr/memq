package io.appform.memq.observer;

import io.appform.memq.actor.ActorOperation;
import io.appform.memq.actor.Message;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class ActorObserverContext<M extends Message> {
    ActorOperation operation;
    M message;
    ObserverMessageMeta messageMeta;
}

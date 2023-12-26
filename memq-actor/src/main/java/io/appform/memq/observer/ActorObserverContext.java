package io.appform.memq.observer;

import io.appform.memq.actor.ActorOperation;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class ActorObserverContext {
    ActorOperation operation;
}

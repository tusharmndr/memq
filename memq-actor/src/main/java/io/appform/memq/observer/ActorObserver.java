package io.appform.memq.observer;

import io.appform.memq.actor.Actor;
import io.appform.memq.actor.Message;
import lombok.Getter;


public abstract class ActorObserver {
    @Getter
    private ActorObserver next;

    protected ActorObserver(ActorObserver next) {
        this.next = next;
    }

    public abstract void initialize(Actor<?> actor);

    public abstract boolean execute(
            final ActorObserverContext<? extends Message> context,
            final Runnable supplier);

    public final ActorObserver setNext(final ActorObserver next) {
        this.next = next;
        return this;
    }

    protected final boolean proceed(
            final ActorObserverContext<? extends Message> context,
            final Runnable runnable) {
        if (null == next) {
            runnable.run();
            return true;
        }
        return next.execute(context, runnable);
    }

}

package io.appform.memq.observer;

import io.appform.memq.actor.Actor;
import lombok.Getter;


public abstract class ActorObserver {
    @Getter
    private ActorObserver next;

    protected ActorObserver(ActorObserver next) {
        this.next = next;
    }

    public abstract void initialize(Actor actor);

    public abstract void execute(
            final ActorObserverContext context,
            final Runnable supplier);

    public final ActorObserver setNext(final ActorObserver next) {
        this.next = next;
        return this;
    }

    protected final void proceed(
            final ActorObserverContext context,
            final Runnable runnable) {
        if (null == next) {
            runnable.run();
            return;
        }
        next.execute(context, runnable);
    }

}

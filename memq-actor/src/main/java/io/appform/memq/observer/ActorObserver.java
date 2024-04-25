package io.appform.memq.observer;

import io.appform.memq.actor.Actor;
import io.appform.memq.actor.Message;
import lombok.Getter;

import java.util.function.BooleanSupplier;


public abstract class ActorObserver {
    @Getter
    private ActorObserver next;

    protected ActorObserver(ActorObserver next) {
        this.next = next;
    }

    public abstract void initialize(Actor<? extends Message> actor);

    public abstract boolean execute(
            final ActorObserverContext<? extends Message> context,
            final BooleanSupplier supplier);

    public final ActorObserver setNext(final ActorObserver next) {
        this.next = next;
        return this;
    }

    protected final boolean proceed(
            final ActorObserverContext<? extends Message> context,
            final BooleanSupplier supplier) {
        if (null == next) {
            return supplier.getAsBoolean();
        }
        return next.execute(context, supplier);
    }

}

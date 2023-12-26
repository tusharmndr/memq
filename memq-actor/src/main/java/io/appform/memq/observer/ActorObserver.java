package io.appform.memq.observer;

import io.appform.memq.actor.Actor;
import lombok.Getter;

import java.util.function.BooleanSupplier;

public abstract class ActorObserver {
    @Getter
    private ActorObserver next;

    protected ActorObserver(ActorObserver next) {
        this.next = next;
    }

    public abstract Boolean executePublish(final ActorObserverContext context,
                                         final BooleanSupplier supplier);

    public abstract void executeConsume(final ActorObserverContext context,
                                        final Runnable runnable);

    public abstract void executeSideline(final ActorObserverContext context,
                                        final Runnable runnable);

    public abstract void executeExceptionHandler(final ActorObserverContext context,
                                                 final Runnable runnable);

    public final ActorObserver setNext(final ActorObserver next) {
        this.next = next;
        return this;
    }

    protected final Boolean proceedPublish(final ActorObserverContext context,
                                         final BooleanSupplier supplier) {
        if (null == next) {
            return supplier.getAsBoolean();
        }
        return next.executePublish(context, supplier);
    }

    protected final void proceedConsume(final ActorObserverContext context, final Runnable runnable) {
        if (null == next) {
            runnable.run();
            return;
        }
        next.executeConsume(context, runnable);
    }

    protected final void proceedSideline(final ActorObserverContext context, final Runnable runnable) {
        if (null == next) {
            runnable.run();
            return;
        }
        next.executeSideline(context, runnable);
    }

    protected final void proceedExceptionHandler(final ActorObserverContext context, final Runnable runnable) {
        if (null == next) {
            runnable.run();
            return;
        }
        next.executeExceptionHandler(context, runnable);
    }

}

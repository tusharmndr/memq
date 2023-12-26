package io.appform.memq.observer;


import java.util.function.BooleanSupplier;

public final class TerminalActorObserver extends ActorObserver {
    public TerminalActorObserver() {
        super(null);
    }

    @Override
    public Boolean executePublish(final ActorObserverContext context, final BooleanSupplier supplier) {
        return proceedPublish(context, supplier);
    }

    @Override
    public void executeConsume(final ActorObserverContext context, final Runnable runnable) {
        proceedConsume(context, runnable);
    }

    @Override
    public void executeSideline(ActorObserverContext context, Runnable runnable) {
        proceedSideline(context, runnable);
    }

    @Override
    public void executeExceptionHandler(ActorObserverContext context, Runnable runnable) {
        proceedExceptionHandler(context, runnable);
    }
}
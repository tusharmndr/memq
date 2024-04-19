package io.appform.memq.observer;


import io.appform.memq.actor.Actor;
import io.appform.memq.actor.Message;


public final class TerminalActorObserver extends ActorObserver {
    public TerminalActorObserver() {
        super(null);
    }

    @Override
    public void initialize(Actor<?> actor) {
    }

    @Override
    public boolean execute(final ActorObserverContext<? extends Message> context, final Runnable runnable) {
        return proceed(context, runnable);
    }
}
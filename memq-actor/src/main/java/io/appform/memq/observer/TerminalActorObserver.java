package io.appform.memq.observer;


import io.appform.memq.actor.Actor;


public final class TerminalActorObserver extends ActorObserver {
    public TerminalActorObserver() {
        super(null);
    }

    @Override
    public void initialize(Actor actor) {
    }

    @Override
    public void execute(final ActorObserverContext context, final Runnable runnable) {
        proceed(context, runnable);
    }
}
package io.appform.memq.observer.impl;

import io.appform.memq.actor.Actor;
import io.appform.memq.actor.ActorOperation;
import io.appform.memq.actor.Message;
import io.appform.memq.observer.ActorObserver;
import io.appform.memq.observer.ActorObserverContext;
import lombok.extern.slf4j.Slf4j;
import lombok.val;


@Slf4j
public class ThrottleActorObserver extends ActorObserver {

    private final long size;
    private Actor<?> actor;


    public ThrottleActorObserver(long size) {
        super(null);
        this.size = size;
    }

    @Override
    public void initialize(Actor<?> actor) {
        this.actor = actor;
    }

    @Override
    public boolean execute(final ActorObserverContext<? extends Message> context, final Runnable runnable) {
        if (context.getOperation() == ActorOperation.PUBLISH) {
            val currSize = actor.size();
            if (currSize >= this.size) {
                log.warn("Blocking publish for as curr size:{} is more than specified threshold:{}",
                        currSize, this.size);
                return false;
            }
        }
        return proceed(context, runnable);
    }
}

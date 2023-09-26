package io.appform.memq.sideline.impl;


import io.appform.memq.actor.Message;
import io.appform.memq.sideline.SidelineStore;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class NoOpSidelineStore<M extends Message> implements SidelineStore<M> {
    @Override
    public void save(M message) {
        log.info("Ignoring action on sideline message:{}", message);
    }
}

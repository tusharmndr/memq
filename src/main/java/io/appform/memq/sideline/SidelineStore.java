package io.appform.memq.sideline;

import io.appform.memq.actor.Message;

public interface SidelineStore<M extends Message> {
    void save(M message) throws Exception;
}

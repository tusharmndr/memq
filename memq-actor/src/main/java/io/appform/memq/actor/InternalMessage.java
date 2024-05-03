package io.appform.memq.actor;

import lombok.Value;

//package private class

@Value
class InternalMessage<M extends Message> {
    String id;
    long validTill;
    long publishedAt;
    M message;
}

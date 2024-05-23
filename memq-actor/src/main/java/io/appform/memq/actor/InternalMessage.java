package io.appform.memq.actor;

import lombok.Value;

import java.util.Map;


@Value
class InternalMessage<M extends Message> {
    String id;
    long validTill;
    long publishedAt;
    Map<String,Object> headers;
    M message;
}

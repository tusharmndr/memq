package io.appform.memq.observer;

import lombok.Value;

@Value
public class ObserverMessageMeta {
    String id;
    long validTill;
    long publishedAt;
}

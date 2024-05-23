package io.appform.memq.actor;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;


@Getter
@AllArgsConstructor
public class MessageMeta {

    private AtomicInteger deliveryAttempt;
    private final long publishedAt;
    private final long validTill;
    private final Map<String, Object> headers;

    public boolean isRedelivered() {
        return deliveryAttempt.get() > 1;
    }

    //Private package function
    void updateAttempt(int deliveryAttempt) {
        this.deliveryAttempt.set(deliveryAttempt);
    }


}
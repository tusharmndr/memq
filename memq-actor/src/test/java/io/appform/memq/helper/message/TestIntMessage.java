package io.appform.memq.helper.message;

import io.appform.memq.actor.Message;
import lombok.Value;

import java.util.UUID;

@Value
public class TestIntMessage implements Message {
    int value;
    String id = UUID.randomUUID().toString();

    @Override
    public String id() {
        return id;
    }
}

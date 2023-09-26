package io.appform.memq.exceptionhandler.handlers;

import io.appform.memq.exceptionhandler.config.DropConfig;

public class MessageDropHandler extends ExceptionHandler {

    public MessageDropHandler(DropConfig dropConfig) {
        super(dropConfig);
    }

    @Override
    public boolean handle() {
        return true;
    }
}

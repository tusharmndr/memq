package io.appform.memq.exceptionhandler.handlers;

import io.appform.memq.exceptionhandler.config.SidelineConfig;

public class MessageSidelineHandler extends ExceptionHandler {

    public MessageSidelineHandler(SidelineConfig sidelineConfig) {
        super(sidelineConfig);
    }

    @Override
    public boolean handle() {
        return false;
    }
}

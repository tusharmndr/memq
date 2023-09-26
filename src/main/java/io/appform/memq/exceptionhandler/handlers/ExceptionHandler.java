package io.appform.memq.exceptionhandler.handlers;

import io.appform.memq.exceptionhandler.config.ExceptionHandlerConfig;

public abstract class ExceptionHandler {

    private final ExceptionHandlerConfig exceptionHandlerConfig;

    public ExceptionHandler(ExceptionHandlerConfig exceptionHandlerConfig) {
        this.exceptionHandlerConfig = exceptionHandlerConfig;
    }

    abstract public boolean handle();
}

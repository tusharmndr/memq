package io.appform.memq.exceptionhandler;

import io.appform.memq.exceptionhandler.config.DropConfig;
import io.appform.memq.exceptionhandler.config.ExceptionHandlerConfig;
import io.appform.memq.exceptionhandler.config.ExceptionHandlerConfigVisitor;
import io.appform.memq.exceptionhandler.config.SidelineConfig;
import io.appform.memq.exceptionhandler.handlers.ExceptionHandler;
import io.appform.memq.exceptionhandler.handlers.MessageDropHandler;
import io.appform.memq.exceptionhandler.handlers.MessageSidelineHandler;

public class ExceptionHandlingFactory {

    public ExceptionHandler create(ExceptionHandlerConfig config) {
        if (config == null) {
            return new MessageSidelineHandler(new SidelineConfig());
        }
        return config.accept(new ExceptionHandlerConfigVisitor<ExceptionHandler>() {
            @Override
            public ExceptionHandler visit(DropConfig config) {
                return new MessageDropHandler(config);
            }

            @Override
            public ExceptionHandler visit(SidelineConfig config) {
                return new MessageSidelineHandler(config);
            }
        });
    }
}

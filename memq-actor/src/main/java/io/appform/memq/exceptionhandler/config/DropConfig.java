package io.appform.memq.exceptionhandler.config;

import io.appform.memq.exceptionhandler.ExceptionHandlerType;
import lombok.*;
import lombok.extern.jackson.Jacksonized;

@Builder
@Jacksonized
@EqualsAndHashCode(callSuper = true)
public class DropConfig extends ExceptionHandlerConfig {

    public DropConfig() {
        super(ExceptionHandlerType.DROP);
    }

    @Override
    public <T> T accept(ExceptionHandlerConfigVisitor<T> visitor) {
        return visitor.visit(this);
    }
}

package io.appform.memq.exceptionhandler.config;

import io.appform.memq.exceptionhandler.ExceptionHandlerType;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.extern.jackson.Jacksonized;

@Builder
@Jacksonized
@EqualsAndHashCode(callSuper = true)
public class SidelineConfig extends ExceptionHandlerConfig {

    public SidelineConfig() {
        super(ExceptionHandlerType.SIDELINE);
    }

    @Override
    public <T> T accept(ExceptionHandlerConfigVisitor<T> visitor) {
        return visitor.visit(this);
    }
}

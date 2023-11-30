package io.appform.memq.exceptionhandler.config;

import io.appform.memq.exceptionhandler.ExceptionHandlerType;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class DropConfig extends ExceptionHandlerConfig {

    public DropConfig() {
        super(ExceptionHandlerType.DROP);
    }

    @Override
    public <T> T accept(ExceptionHandlerConfigVisitor<T> visitor) {
        return visitor.visit(this);
    }
}

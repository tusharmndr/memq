package io.appform.memq.exceptionhandler.config;

import io.appform.memq.exceptionhandler.ExceptionHandlerType;
import io.appform.memq.ExecutorConfig;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class SidelineConfig extends ExceptionHandlerConfig {

    public SidelineConfig() {
        super(ExceptionHandlerType.SIDELINE);
    }

    @Override
    public <T> T accept(ExceptionHandlerConfigVisitor<T> visitor) {
        return visitor.visit(this);
    }
}

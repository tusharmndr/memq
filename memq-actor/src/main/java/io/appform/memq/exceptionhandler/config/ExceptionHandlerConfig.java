package io.appform.memq.exceptionhandler.config;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import io.appform.memq.exceptionhandler.ExceptionHandlerType;


@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.EXISTING_PROPERTY, property = "type",
        defaultImpl = SidelineConfig.class)
@JsonSubTypes({
        @JsonSubTypes.Type(name = "SIDELINE", value = SidelineConfig.class),
        @JsonSubTypes.Type(name = "DROP", value = DropConfig.class)
})
public abstract class ExceptionHandlerConfig {

    ExceptionHandlerType type;

    protected ExceptionHandlerConfig(ExceptionHandlerType type) {
        this.type = type;
    }

    public abstract <T> T accept(ExceptionHandlerConfigVisitor<T> visitor);
}

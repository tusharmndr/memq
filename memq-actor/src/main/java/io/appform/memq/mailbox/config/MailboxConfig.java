package io.appform.memq.mailbox.config;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import io.appform.memq.mailbox.MailboxType;


@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.EXISTING_PROPERTY, property = "type",
        defaultImpl = UnBoundedMailboxConfig.class)
@JsonSubTypes({
        @JsonSubTypes.Type(name = "BOUNDED", value = BoundedMailboxConfig.class),
        @JsonSubTypes.Type(name = "UNBOUNDED", value = UnBoundedMailboxConfig.class)
})
public abstract class MailboxConfig {

    MailboxType type;

    protected MailboxConfig(MailboxType type) {
        this.type = type;
    }

    public abstract <T> T accept(MailboxConfigVisitor<T> visitor);
}

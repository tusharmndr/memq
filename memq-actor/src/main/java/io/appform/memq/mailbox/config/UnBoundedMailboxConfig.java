package io.appform.memq.mailbox.config;

import io.appform.memq.mailbox.MailboxType;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

@Value
@EqualsAndHashCode(callSuper = true)
public class UnBoundedMailboxConfig extends MailboxConfig {

    @Builder
    @Jacksonized
    public UnBoundedMailboxConfig() {
        super(MailboxType.UNBOUNDED);
    }

    @Override
    public <T> T accept(MailboxConfigVisitor<T> visitor) {
        return visitor.visit(this);
    }
}
package io.appform.memq.mailbox.config;

import io.appform.memq.mailbox.MailboxType;
import io.appform.memq.retry.RetryType;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

import java.util.Set;


@Value
@EqualsAndHashCode(callSuper = true)
public class BoundedMailboxConfig extends MailboxConfig {

    long maxSize;

    @Builder
    @Jacksonized
    public BoundedMailboxConfig(long maxSize) {
        super(MailboxType.BOUNDED);
        this.maxSize = maxSize;
    }

    @Override
    public <T> T accept(MailboxConfigVisitor<T> visitor) {
        return visitor.visit(this);
    }
}
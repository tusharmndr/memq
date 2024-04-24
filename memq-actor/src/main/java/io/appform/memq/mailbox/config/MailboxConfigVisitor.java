package io.appform.memq.mailbox.config;

public interface MailboxConfigVisitor<T> {
    T visit(BoundedMailboxConfig boundedMailboxConfig);

    T visit(UnBoundedMailboxConfig unBoundedMailboxConfig);
}

package io.appform.memq.mailbox;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

@Value
@Builder
@Jacksonized
@AllArgsConstructor
public class MailboxConfig {

    long maxSizePerPartition;

    public MailboxConfig() {
        this.maxSizePerPartition = Long.MAX_VALUE;
    }

}

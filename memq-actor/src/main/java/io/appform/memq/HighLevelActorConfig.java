package io.appform.memq;

import io.appform.memq.exceptionhandler.config.DropConfig;
import io.appform.memq.exceptionhandler.config.ExceptionHandlerConfig;
import io.appform.memq.mailbox.MailboxConfig;
import io.appform.memq.retry.config.NoRetryConfig;
import io.appform.memq.retry.config.RetryConfig;
import lombok.*;
import lombok.extern.jackson.Jacksonized;

import javax.validation.Valid;
import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;

@Value
@Builder
@Jacksonized
@AllArgsConstructor
@NoArgsConstructor
public class HighLevelActorConfig {

    @Min(1)
    @Max(100)
    @Builder.Default
    int partitions = 1;

    @Valid
    @NotNull
    @Builder.Default
    RetryConfig retryConfig = new NoRetryConfig();

    @Valid
    @NotNull
    @Builder.Default
    ExceptionHandlerConfig exceptionHandlerConfig = new DropConfig();

    @Valid
    @Builder.Default
    MailboxConfig mailboxConfig = new MailboxConfig();

    @NotNull
    @Builder.Default
    String executorName = "default";

    @Builder.Default
    boolean metricDisabled = false;

}

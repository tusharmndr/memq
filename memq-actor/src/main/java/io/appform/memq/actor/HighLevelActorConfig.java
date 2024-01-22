package io.appform.memq.actor;

import io.appform.memq.exceptionhandler.config.DropConfig;
import io.appform.memq.exceptionhandler.config.ExceptionHandlerConfig;
import io.appform.memq.retry.config.NoRetryConfig;
import io.appform.memq.retry.config.RetryConfig;
import lombok.*;

import javax.validation.Valid;
import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;

@Data
@Builder
@ToString
@EqualsAndHashCode
@AllArgsConstructor
@NoArgsConstructor
public class HighLevelActorConfig {

    @Min(1)
    @Max(100)
    @Builder.Default
    private int partitions = 1;

    @Valid
    @NotNull
    @Builder.Default
    private RetryConfig retryConfig = new NoRetryConfig();

    @Valid
    @NotNull
    @Builder.Default
    private ExceptionHandlerConfig exceptionHandlerConfig = new DropConfig();

    @NotNull
    private String executorName;

    private boolean metricDisabled;

}

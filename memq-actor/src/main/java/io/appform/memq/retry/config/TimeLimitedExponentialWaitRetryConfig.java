package io.appform.memq.retry.config;

import io.appform.memq.retry.RetryType;
import lombok.*;
import lombok.extern.jackson.Jacksonized;

import javax.validation.Valid;
import javax.validation.constraints.DecimalMin;
import javax.validation.constraints.NotNull;
import java.time.Duration;
import java.util.Set;

@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class TimeLimitedExponentialWaitRetryConfig extends RetryConfig {

    @Valid
    @NotNull
    @Builder.Default
    private Duration waitTime = Duration.ofMillis(10);

    @Valid
    @NotNull
    @Builder.Default
    private Duration maxWaitTime =  Duration.ofMillis(1_000);

    @DecimalMin("1.1")
    @Builder.Default
    private double multipier = 2.0D;

    @Valid
    @Builder.Default
    private Duration maxTime = Duration.ofMillis(30_000);

    @Builder
    @Jacksonized
    public TimeLimitedExponentialWaitRetryConfig(
            Duration maxTime,
            Duration waitTime,
            Duration maxWaitTime,
            double multipier,
            Set<String> retriableExceptions) {
        super(RetryType.TIME_LIMITED_EXPONENTIAL_BACKOFF, retriableExceptions);
        this.maxTime = maxTime;
        this.waitTime = waitTime;
        this.maxWaitTime = maxWaitTime;
        this.multipier = multipier;
    }
}

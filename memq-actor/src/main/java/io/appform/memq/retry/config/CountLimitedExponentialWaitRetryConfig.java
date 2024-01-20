package io.appform.memq.retry.config;

import io.appform.memq.retry.RetryType;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.extern.jackson.Jacksonized;

import javax.validation.Valid;
import javax.validation.constraints.DecimalMin;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;
import java.time.Duration;
import java.util.Set;

@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class CountLimitedExponentialWaitRetryConfig extends RetryConfig {

    @Min(1)
    @Builder.Default
    private int maxAttempts = 1;

    @Valid
    @NotNull
    @Builder.Default
    private Duration waitTime = Duration.ofMillis(10);

    @Valid
    @NotNull
    @Builder.Default
    private Duration maxWaitTime = Duration.ofMillis(1_000);

    @DecimalMin("1.1")
    @Builder.Default
    private double multipier = 2.0D;

    @Builder
    @Jacksonized
    public CountLimitedExponentialWaitRetryConfig(
            int maxAttempts,
            Duration waitTime,
            Duration maxWaitTime,
            double multipier,
            Set<String> retriableExceptions) {
        super(RetryType.COUNT_LIMITED_EXPONENTIAL_BACKOFF, retriableExceptions);
        this.maxAttempts = maxAttempts;
        this.waitTime = waitTime;
        this.maxWaitTime = maxWaitTime;
        this.multipier = multipier;
    }
}

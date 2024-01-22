package io.appform.memq.retry.config;

import io.appform.memq.retry.RetryType;
import lombok.*;
import lombok.extern.jackson.Jacksonized;

import javax.validation.constraints.DecimalMin;
import javax.validation.constraints.Min;
import java.util.Set;

@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class TimeLimitedExponentialWaitRetryConfig extends RetryConfig {

    @Min(1)
    @Builder.Default
    private int waitTimeInMillis = 10;

    @Min(2)
    @Builder.Default
    private int maxWaitTimeInMillis = 1_000;

    @DecimalMin("1.1")
    @Builder.Default
    private double multipier = 2.0D;

    @Min(3)
    @Builder.Default
    private int maxTimeInMillis = 10_000;

    @Builder
    @Jacksonized
    public TimeLimitedExponentialWaitRetryConfig(
            int maxTimeInMillis,
            int waitTimeInMillis,
            int maxWaitTimeInMillis,
            double multipier,
            Set<String> retriableExceptions) {
        super(RetryType.TIME_LIMITED_EXPONENTIAL_BACKOFF, retriableExceptions);
        this.maxTimeInMillis = maxTimeInMillis;
        this.waitTimeInMillis = waitTimeInMillis;
        this.maxWaitTimeInMillis = maxWaitTimeInMillis;
        this.multipier = multipier;
    }
}

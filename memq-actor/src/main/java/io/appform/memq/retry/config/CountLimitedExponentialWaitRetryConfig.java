package io.appform.memq.retry.config;

import io.appform.memq.retry.RetryType;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.extern.jackson.Jacksonized;

import javax.validation.constraints.DecimalMin;
import javax.validation.constraints.Min;
import java.util.Set;

@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class CountLimitedExponentialWaitRetryConfig extends RetryConfig {

    @Min(1)
    @Builder.Default
    private int maxAttempts = 1;

    @Min(1)
    @Builder.Default
    private int waitTimeInMillis = 10;

    @Min(2)
    @Builder.Default
    private int maxWaitTimeInMillis = 1_000;

    @DecimalMin("1.1")
    @Builder.Default
    private double multipier = 2.0D;

    @Builder
    @Jacksonized
    public CountLimitedExponentialWaitRetryConfig(
            int maxAttempts,
            int waitTimeInMillis,
            int maxWaitTimeInMillis,
            double multipier,
            Set<String> retriableExceptions) {
        super(RetryType.COUNT_LIMITED_EXPONENTIAL_BACKOFF, retriableExceptions);
        this.maxAttempts = maxAttempts;
        this.waitTimeInMillis = waitTimeInMillis;
        this.maxWaitTimeInMillis = maxWaitTimeInMillis;
        this.multipier = multipier;
    }
}

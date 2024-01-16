package io.appform.memq.retry.config;

import io.appform.memq.retry.RetryType;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import javax.validation.constraints.DecimalMin;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;
import java.util.Set;

@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class TimeLimitedExponentialWaitRetryConfig extends RetryConfig {

    @NotNull
    @Builder.Default
    private int maxTimeInMillis = 1000;

    @Min(1)
    @Builder.Default
    int delayInMillis = 1;

    @Min(2)
    @Builder.Default
    int maxDelayInMillis = 2;

    @Min(1)
    @Builder.Default
    int maxTimeInMillisBetweenRetries = 1;

    @DecimalMin("1.1")
    @Builder.Default
    double multipier = 2.0D;

    public TimeLimitedExponentialWaitRetryConfig() {
        super(RetryType.TIME_LIMITED_EXPONENTIAL_BACKOFF);
    }

    @Builder
    public TimeLimitedExponentialWaitRetryConfig(int maxTimeInMillis,
                                                 int delayInMillis,
                                                 int maxTimeInMillisBetweenRetries,
                                                 double multipier,
                                                 Set<String> retriableExceptions) {
        super(RetryType.TIME_LIMITED_EXPONENTIAL_BACKOFF, retriableExceptions);
        this.maxTimeInMillis = maxTimeInMillis;
        this.delayInMillis = delayInMillis;
        this.maxTimeInMillisBetweenRetries = maxTimeInMillisBetweenRetries;
        this.multipier = multipier;
    }
}

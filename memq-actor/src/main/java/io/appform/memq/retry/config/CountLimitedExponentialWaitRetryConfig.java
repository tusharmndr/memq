

package io.appform.memq.retry.config;

import io.appform.memq.retry.RetryType;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import javax.validation.constraints.DecimalMin;
import javax.validation.constraints.Min;
import java.util.Set;

@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class CountLimitedExponentialWaitRetryConfig extends RetryConfig {

    @Min(1)
    @Builder.Default
    int maxAttempts = 1;

    @Min(1)
    @Builder.Default
    int delayInMillis = 1;

    @Min(2)
    @Builder.Default
    int maxDelayInMillis = 100;

    @Min(1)
    @Builder.Default
    int maxTimeInMillisBetweenRetries = 1;

    @DecimalMin("1.1")
    @Builder.Default
    double multipier = 2.0D;

    public CountLimitedExponentialWaitRetryConfig() {
        super(RetryType.COUNT_LIMITED_EXPONENTIAL_BACKOFF);
    }

    @Builder
    public CountLimitedExponentialWaitRetryConfig(int maxAttempts,
                                                  int delayInMillis,
                                                  int maxTimeInMillisBetweenRetries,
                                                  double multipier,
                                                  Set<String> retriableExceptions) {
        super(RetryType.COUNT_LIMITED_EXPONENTIAL_BACKOFF, retriableExceptions);
        this.maxAttempts = maxAttempts;
        this.delayInMillis = delayInMillis;
        this.maxTimeInMillisBetweenRetries = maxTimeInMillisBetweenRetries;
        this.multipier = multipier;
    }
}



package io.appform.memq.retry.config;

import io.appform.memq.retry.RetryType;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import javax.validation.constraints.Min;
import java.util.Set;

@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class CountLimitedRandomWaitRetryConfig extends RetryConfig {

    @Min(1)
    @Builder.Default
    int maxAttempts = 1;

    @Min(1)
    @Builder.Default
    int minDelayInMillis = 1;

    @Min(1)
    @Builder.Default
    int maxDelayInMillis = 1;

    public CountLimitedRandomWaitRetryConfig() {
        super(RetryType.COUNT_LIMITED_RANDOM_WAIT);
    }

    @Builder
    public CountLimitedRandomWaitRetryConfig(int maxAttempts,
                                             int minDelayInMillis,
                                             int maxDelayInMillis,
                                             Set<String> retriableExceptions) {
        super(RetryType.COUNT_LIMITED_RANDOM_WAIT, retriableExceptions);
        this.maxAttempts = maxAttempts;
        this.minDelayInMillis = minDelayInMillis;
        this.maxDelayInMillis = maxDelayInMillis;
    }
}



package io.appform.memq.retry.config;

import io.appform.memq.retry.RetryType;
import io.dropwizard.util.Duration;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import javax.validation.constraints.Min;
import java.util.Set;

@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class CountLimitedFixedWaitRetryConfig extends RetryConfig {

    @Min(1)
    @Builder.Default
    int maxAttempts = 1;

    @Min(1)
    @Builder.Default
    int delayInMillis = 1;

    public CountLimitedFixedWaitRetryConfig() {
        super(RetryType.COUNT_LIMITED_FIXED_WAIT);
    }

    @Builder
    public CountLimitedFixedWaitRetryConfig(int maxAttempts, int delayInMillis, Set<String> retriableExceptions) {
        super(RetryType.COUNT_LIMITED_FIXED_WAIT, retriableExceptions);
        this.maxAttempts = maxAttempts;
        this.delayInMillis = delayInMillis;
    }
}

package io.appform.memq.retry.config;

import io.appform.memq.retry.RetryType;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.extern.jackson.Jacksonized;

import javax.validation.constraints.Min;
import java.util.Set;

@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class TimeLimitedRandomWaitRetryConfig extends RetryConfig {

    @Min(1)
    @Builder.Default
    private int minWaiTimeInMillis = 10;

    @Min(2)
    @Builder.Default
    private int maxWaitTimeInMillis = 1_000;

    @Min(3)
    @Builder.Default
    private int maxTimeInMillis = 10_000;

    @Builder
    @Jacksonized
    public TimeLimitedRandomWaitRetryConfig(
            int maxTimeInMillis,
            int minWaiTimeInMillis,
            int maxWaitTimeInMillis,
            Set<String> retriableExceptions) {
        super(RetryType.TIME_LIMITED_RANDOM_WAIT, retriableExceptions);
        this.maxTimeInMillis = maxTimeInMillis;
        this.minWaiTimeInMillis = minWaiTimeInMillis;
        this.maxWaitTimeInMillis = maxWaitTimeInMillis;
    }
}

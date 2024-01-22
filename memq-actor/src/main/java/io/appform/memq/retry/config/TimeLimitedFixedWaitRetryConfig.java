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
public class TimeLimitedFixedWaitRetryConfig extends RetryConfig {

    @Min(1)
    @Builder.Default
    private int waitTimeInMillis = 500;

    @Min(2)
    @Builder.Default
    private int maxTimeInMillis = 10_000;

    @Builder
    @Jacksonized
    public TimeLimitedFixedWaitRetryConfig(
            int maxTimeInMillis,
            int waitTimeInMillis,
            Set<String> retriableExceptions) {
        super(RetryType.TIME_LIMITED_FIXED_WAIT, retriableExceptions);
        this.maxTimeInMillis = maxTimeInMillis;
        this.waitTimeInMillis = waitTimeInMillis;
    }
}

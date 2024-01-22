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
public class CountLimitedFixedWaitRetryConfig extends RetryConfig {

    @Min(1)
    @Builder.Default
    private int maxAttempts = 1;

    @Min(1)
    @Builder.Default
    private int waitTimeInMillis = 500;

    @Builder
    @Jacksonized
    public CountLimitedFixedWaitRetryConfig(int maxAttempts, int waitTimeInMillis, Set<String> retriableExceptions) {
        super(RetryType.COUNT_LIMITED_FIXED_WAIT, retriableExceptions);
        this.maxAttempts = maxAttempts;
        this.waitTimeInMillis = waitTimeInMillis;
    }
}

package io.appform.memq.retry.config;

import io.appform.memq.retry.RetryType;
import lombok.*;
import lombok.extern.jackson.Jacksonized;

import javax.validation.constraints.Min;
import java.util.Set;

@Value
@EqualsAndHashCode(callSuper = true)
public class CountLimitedFixedWaitRetryConfig extends RetryConfig {

    @Min(1)
    int maxAttempts;

    @Min(1)
    int waitTimeInMillis;

    @Builder
    @Jacksonized
    public CountLimitedFixedWaitRetryConfig(int maxAttempts,
                                            int waitTimeInMillis,
                                            Set<String> retriableExceptions) {
        super(RetryType.COUNT_LIMITED_FIXED_WAIT, retriableExceptions);
        this.maxAttempts = maxAttempts;
        this.waitTimeInMillis = waitTimeInMillis;
    }
}

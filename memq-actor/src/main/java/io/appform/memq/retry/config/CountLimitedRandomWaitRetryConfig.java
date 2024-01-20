package io.appform.memq.retry.config;

import io.appform.memq.retry.RetryType;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.extern.jackson.Jacksonized;

import javax.validation.Valid;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;
import java.time.Duration;
import java.util.Set;

@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class CountLimitedRandomWaitRetryConfig extends RetryConfig {

    @Min(1)
    @Builder.Default
    private int maxAttempts = 1;

    @Valid
    @NotNull
    @Builder.Default
    private Duration minWaitTime = Duration.ofMillis(10);

    @Valid
    @NotNull
    @Builder.Default
    private Duration maxWaitTime = Duration.ofMillis(1_000);

    @Builder
    @Jacksonized
    CountLimitedRandomWaitRetryConfig(
            int maxAttempts,
            Duration minWaitTime,
            Duration maxWaitTime,
            Set<String> retriableExceptions) {
        super(RetryType.COUNT_LIMITED_RANDOM_WAIT, retriableExceptions);
        this.maxAttempts = maxAttempts;
        this.minWaitTime = minWaitTime;
        this.maxWaitTime = maxWaitTime;
    }
}

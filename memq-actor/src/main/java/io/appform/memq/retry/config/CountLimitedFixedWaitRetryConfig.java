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
public class CountLimitedFixedWaitRetryConfig extends RetryConfig {

    @Min(1)
    @Builder.Default
    private int maxAttempts = 1;

    @Valid
    @NotNull
    @Builder.Default
    private Duration waitTime = Duration.ofMillis(500);

    @Builder
    @Jacksonized
    public CountLimitedFixedWaitRetryConfig(int maxAttempts, Duration waitTime, Set<String> retriableExceptions) {
        super(RetryType.COUNT_LIMITED_FIXED_WAIT, retriableExceptions);
        this.maxAttempts = maxAttempts;
        this.waitTime = waitTime;
    }
}

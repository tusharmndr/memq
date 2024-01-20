package io.appform.memq.retry.config;

import io.appform.memq.retry.RetryType;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.extern.jackson.Jacksonized;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import java.time.Duration;
import java.util.Set;

@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class TimeLimitedRandomWaitRetryConfig extends RetryConfig {

    @Valid
    @NotNull
    @Builder.Default
    private Duration minWaiTime = Duration.ofMillis(10);

    @Valid
    @NotNull
    @Builder.Default
    private Duration maxWaitTime = Duration.ofMillis(1_000);

    @Valid
    @NotNull
    @Builder.Default
    private Duration maxTime = Duration.ofMillis(30_000);

    @Builder
    @Jacksonized
    public TimeLimitedRandomWaitRetryConfig(
            Duration maxTime,
            Duration minWaiTime,
            Duration maxWaitTime,
            Set<String> retriableExceptions) {
        super(RetryType.TIME_LIMITED_RANDOM_WAIT, retriableExceptions);
        this.maxTime = maxTime;
        this.minWaiTime = minWaiTime;
        this.maxWaitTime = maxWaitTime;
    }
}

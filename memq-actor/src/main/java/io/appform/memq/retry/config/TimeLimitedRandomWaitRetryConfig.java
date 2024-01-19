package io.appform.memq.retry.config;

import io.appform.memq.retry.RetryType;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.extern.jackson.Jacksonized;

import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;
import java.util.Set;

@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class TimeLimitedRandomWaitRetryConfig extends RetryConfig {

    @Min(1)
    @Builder.Default
    int minDelayInMillis = 1;
    @Min(1)
    @Builder.Default
    int maxDelayInMillis = 1;
    @NotNull
    @Builder.Default
    private int maxTimeInMillis = 1000;

    @Builder
    @Jacksonized
    public TimeLimitedRandomWaitRetryConfig(
            int maxTimeInMillis,
            int minDelayInMillis,
            int maxDelayInMillis,
            Set<String> retriableExceptions) {
        super(RetryType.TIME_LIMITED_RANDOM_WAIT, retriableExceptions);
        this.maxTimeInMillis = maxTimeInMillis;
        this.minDelayInMillis = minDelayInMillis;
        this.maxDelayInMillis = maxDelayInMillis;
    }
}

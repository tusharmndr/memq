

package io.appform.memq.retry.config;

import io.appform.memq.retry.RetryType;
import io.dropwizard.util.Duration;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import javax.validation.Valid;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;
import java.util.Set;

@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class TimeLimitedRandomWaitRetryConfig extends RetryConfig {

    @NotNull
    @Builder.Default
    private int maxTimeInMillis = 1000;

    @Min(1)
    @Builder.Default
    int minDelayInMillis = 1;

    @Min(1)
    @Builder.Default
    int maxDelayInMillis = 1;

    public TimeLimitedRandomWaitRetryConfig() {
        super(RetryType.TIME_LIMITED_RANDOM_WAIT);
    }

    @Builder
    public TimeLimitedRandomWaitRetryConfig(int maxTimeInMillis,
                                            int minDelayInMillis,
                                            int maxDelayInMillis,
                                            Set<String> retriableExceptions) {
        super(RetryType.TIME_LIMITED_RANDOM_WAIT, retriableExceptions);
        this.maxTimeInMillis = maxTimeInMillis;
        this.minDelayInMillis = minDelayInMillis;
        this.maxDelayInMillis = maxDelayInMillis;
    }
}

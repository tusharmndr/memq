

package io.appform.memq.retry.config;

import io.appform.memq.retry.RetryType;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;
import java.util.Set;

@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class TimeLimitedFixedWaitRetryConfig extends RetryConfig {

    @NotNull
    @Builder.Default
    private int maxTimeInMillis = 1000;


    @Min(1)
    @Builder.Default
    int delayInMillis = 1;

    public TimeLimitedFixedWaitRetryConfig() {
        super(RetryType.TIME_LIMITED_FIXED_WAIT);
    }

    @Builder
    public TimeLimitedFixedWaitRetryConfig(int maxTimeInMillis,
                                           int delayInMillis,
                                           Set<String> retriableExceptions) {
        super(RetryType.TIME_LIMITED_FIXED_WAIT, retriableExceptions);
        this.maxTimeInMillis = maxTimeInMillis;
        this.delayInMillis = delayInMillis;
    }
}

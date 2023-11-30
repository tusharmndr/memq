

package io.appform.memq.retry.impl;

import io.appform.memq.retry.RetryStrategy;
import io.appform.memq.retry.config.TimeLimitedRandomWaitRetryConfig;
import io.appform.memq.utils.CommonUtils;
import net.jodah.failsafe.RetryPolicy;

import java.time.Duration;
import java.time.temporal.ChronoUnit;

public class TimeLimitedRandomWaitRetryStrategy extends RetryStrategy {
    public TimeLimitedRandomWaitRetryStrategy(TimeLimitedRandomWaitRetryConfig config) {
        super(new RetryPolicy<Boolean>()
                .handleIf(exception -> CommonUtils.isRetriable(config.getRetriableExceptions(), exception))
                .withMaxDuration(Duration.of(config.getMaxTimeInMillis(), ChronoUnit.MILLIS))
                .withDelay(config.getMinDelayInMillis(), config.getMaxDelayInMillis(), ChronoUnit.MILLIS)
        );
    }
}

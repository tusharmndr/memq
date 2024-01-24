package io.appform.memq.retry.impl;

import io.appform.memq.retry.RetryStrategy;
import io.appform.memq.retry.config.TimeLimitedFixedWaitRetryConfig;
import io.appform.memq.utils.CommonUtils;
import net.jodah.failsafe.RetryPolicy;

import java.time.Duration;


public class TimeLimitedFixedWaitRetryStrategy extends RetryStrategy {
    public TimeLimitedFixedWaitRetryStrategy(TimeLimitedFixedWaitRetryConfig config) {
        super(new RetryPolicy<Boolean>()
                      .handleIf(exception -> CommonUtils.isRetriable(config.getRetriableExceptions(), exception))
                      .withMaxDuration(Duration.ofMillis(config.getMaxTimeInMillis()))
                      .withDelay(Duration.ofMillis(config.getWaitTimeInMillis()))
                      .withMaxRetries(-1)
             );
    }
}

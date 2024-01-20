package io.appform.memq.retry.impl;

import io.appform.memq.retry.RetryStrategy;
import io.appform.memq.retry.config.TimeLimitedFixedWaitRetryConfig;
import io.appform.memq.utils.CommonUtils;
import net.jodah.failsafe.RetryPolicy;


public class TimeLimitedFixedWaitRetryStrategy extends RetryStrategy {
    public TimeLimitedFixedWaitRetryStrategy(TimeLimitedFixedWaitRetryConfig config) {
        super(new RetryPolicy<Boolean>()
                      .handleIf(exception -> CommonUtils.isRetriable(config.getRetriableExceptions(), exception))
                      .withMaxDuration(config.getMaxTime())
                      .withDelay(config.getWaitTime())
                      .withMaxRetries(-1)
             );
    }
}

package io.appform.memq.retry.impl;

import io.appform.memq.retry.RetryStrategy;
import io.appform.memq.retry.config.CountLimitedFixedWaitRetryConfig;
import io.appform.memq.utils.CommonUtils;
import net.jodah.failsafe.RetryPolicy;


public class CountLimitedFixedWaitRetryStrategy extends RetryStrategy {
    public CountLimitedFixedWaitRetryStrategy(CountLimitedFixedWaitRetryConfig config) {
        super(new RetryPolicy<Boolean>()
                      .handleIf(exception -> CommonUtils.isRetriable(config.getRetriableExceptions(), exception))
                      .withMaxAttempts(config.getMaxAttempts())
                      .withDelay(config.getWaitTime())
             );
    }
}

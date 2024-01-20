package io.appform.memq.retry.impl;


import io.appform.memq.retry.RetryStrategy;
import io.appform.memq.retry.config.CountLimitedRandomWaitRetryConfig;
import io.appform.memq.utils.CommonUtils;
import net.jodah.failsafe.RetryPolicy;

import java.time.temporal.ChronoUnit;

public class CountLimitedRandomWaitRetryStrategy extends RetryStrategy {
    public CountLimitedRandomWaitRetryStrategy(CountLimitedRandomWaitRetryConfig config) {
        super(new RetryPolicy<Boolean>()
                      .handleIf(exception -> CommonUtils.isRetriable(config.getRetriableExceptions(), exception))
                      .withMaxAttempts(config.getMaxAttempts())
                      .withDelay(config.getMinWaitTime().toMillis(),
                                 config.getMaxWaitTime().toMillis(),
                                 ChronoUnit.MILLIS)
             );
    }
}


package io.appform.memq.retry.impl;


import io.appform.memq.retry.RetryStrategy;
import io.appform.memq.retry.config.CountLimitedExponentialWaitRetryConfig;
import io.appform.memq.utils.CommonUtils;
import net.jodah.failsafe.RetryPolicy;

import java.time.temporal.ChronoUnit;

/**
 * Limits retry time
 */
public class CountLimitedExponentialWaitRetryStrategy extends RetryStrategy {

    public CountLimitedExponentialWaitRetryStrategy(CountLimitedExponentialWaitRetryConfig config) {
        super(new RetryPolicy<Boolean>()
                .handleIf(exception -> CommonUtils.isRetriable(config.getRetriableExceptions(), exception))
                .withMaxAttempts(config.getMaxAttempts())
                .withBackoff(config.getDelayInMillis(),
                        config.getMaxDelayInMillis(),
                        ChronoUnit.MILLIS,
                        config.getMultipier())
        );
    }
}

package io.appform.memq.retry.impl;

import io.appform.memq.retry.RetryStrategy;
import io.appform.memq.retry.config.NoRetryConfig;
import net.jodah.failsafe.RetryPolicy;

/**
 * No retries
 */
public class NoRetryStrategy extends RetryStrategy {
    @SuppressWarnings("unused")
    public NoRetryStrategy(NoRetryConfig config) {
        super(new RetryPolicy<Boolean>()
                      .withMaxAttempts(1));
    }
}

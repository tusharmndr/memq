
package io.appform.memq.retry;

import io.appform.memq.retry.config.*;
import io.appform.memq.retry.impl.*;

/**
 * Creates Policy based on config
 */
public class RetryStrategyFactory {
    public RetryStrategy create(RetryConfig config) {
        return switch (config.getType()) {
            case NO_RETRY -> new NoRetryStrategy(NoRetryConfig.class.cast(config));
            case TIME_LIMITED_EXPONENTIAL_BACKOFF -> new TimeLimitedExponentialWaitRetryStrategy(TimeLimitedExponentialWaitRetryConfig.class.cast(config));
            case TIME_LIMITED_RANDOM_WAIT -> new TimeLimitedRandomWaitRetryStrategy(TimeLimitedRandomWaitRetryConfig.class.cast(config));
            case TIME_LIMITED_FIXED_WAIT -> new TimeLimitedFixedWaitRetryStrategy(TimeLimitedFixedWaitRetryConfig.class.cast(config));
            case COUNT_LIMITED_EXPONENTIAL_BACKOFF -> new CountLimitedExponentialWaitRetryStrategy(CountLimitedExponentialWaitRetryConfig.class.cast(config));
            case COUNT_LIMITED_RANDOM_WAIT -> new CountLimitedRandomWaitRetryStrategy(CountLimitedRandomWaitRetryConfig.class.cast(config));
            case COUNT_LIMITED_FIXED_WAIT -> new CountLimitedFixedWaitRetryStrategy(CountLimitedFixedWaitRetryConfig.class.cast(config));
            default -> null;
        };
    }
}

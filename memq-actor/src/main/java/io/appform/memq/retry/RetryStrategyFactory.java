package io.appform.memq.retry;

import io.appform.memq.retry.config.*;
import io.appform.memq.retry.impl.*;

/**
 * Creates Policy based on config
 */
public class RetryStrategyFactory {
    public RetryStrategy create(RetryConfig config) {
        return switch (config.getType()) {
            case NO_RETRY -> new NoRetryStrategy((NoRetryConfig) config);
            case TIME_LIMITED_EXPONENTIAL_BACKOFF -> new TimeLimitedExponentialWaitRetryStrategy(
                    (TimeLimitedExponentialWaitRetryConfig) config);
            case TIME_LIMITED_RANDOM_WAIT ->
                    new TimeLimitedRandomWaitRetryStrategy((TimeLimitedRandomWaitRetryConfig) config);
            case TIME_LIMITED_FIXED_WAIT ->
                    new TimeLimitedFixedWaitRetryStrategy((TimeLimitedFixedWaitRetryConfig) config);
            case COUNT_LIMITED_EXPONENTIAL_BACKOFF -> new CountLimitedExponentialWaitRetryStrategy(
                    (CountLimitedExponentialWaitRetryConfig) config);
            case COUNT_LIMITED_RANDOM_WAIT ->
                    new CountLimitedRandomWaitRetryStrategy((CountLimitedRandomWaitRetryConfig) config);
            case COUNT_LIMITED_FIXED_WAIT ->
                    new CountLimitedFixedWaitRetryStrategy((CountLimitedFixedWaitRetryConfig) config);
        };
    }
}

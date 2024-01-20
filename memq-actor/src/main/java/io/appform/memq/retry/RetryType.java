package io.appform.memq.retry;


public enum RetryType {
    NO_RETRY,
    TIME_LIMITED_EXPONENTIAL_BACKOFF,
    TIME_LIMITED_RANDOM_WAIT,
    TIME_LIMITED_FIXED_WAIT,
    COUNT_LIMITED_EXPONENTIAL_BACKOFF,
    COUNT_LIMITED_RANDOM_WAIT,
    COUNT_LIMITED_FIXED_WAIT
}
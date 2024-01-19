

package io.appform.memq.retry.config;

import io.appform.memq.retry.RetryType;

/**
 * No retry will be done
 */
public class NoRetryConfig extends RetryConfig {
    public NoRetryConfig() {
        super(RetryType.NO_RETRY);
    }
}

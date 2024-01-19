package io.appform.memq.actor;

/**
 *
 */
public interface Message {
    String id();

    default long validTill() {
        return Long.MAX_VALUE;
    }
}

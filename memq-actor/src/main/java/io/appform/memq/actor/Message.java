package io.appform.memq.actor;

import java.util.Collections;
import java.util.Map;

/**
 *
 */
public interface Message {
    String id();

    default long validTill() {
        return Long.MAX_VALUE;
    }

    default Map<String,Object> headers() {
        return Collections.emptyMap();
    }
}

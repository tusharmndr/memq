package io.appform.memq.stats;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class MetricKeyData {
    String actorName;
    String operation;
}

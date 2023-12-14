package io.appform.memq.stats;

import java.time.Duration;

public interface ActorMetrics {

    void markPublishSuccess();

    void markPublishFailed();

    void markProcessing();

    void markSideline();

    void markReleased();

    void updateProcessTime(Duration elapsed);
}

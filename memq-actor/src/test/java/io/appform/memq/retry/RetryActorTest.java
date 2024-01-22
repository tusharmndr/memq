package io.appform.memq.retry;

import com.google.common.base.Stopwatch;
import io.appform.memq.Constants;
import io.appform.memq.actor.HighLevelActorConfig;
import io.appform.memq.exceptionhandler.config.SidelineConfig;
import io.appform.memq.helper.message.TestIntMessage;
import io.appform.memq.retry.config.*;
import io.appform.memq.helper.TestUtil;
import lombok.SneakyThrows;
import lombok.val;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RetryActorTest {

    static int MAX_RETRY_COUNT = 6;
    static int MAX_RETRY_TIME = 3_000;

    @Test
    void testCountLimitedExponentialWaitRetry() {

        val counter = triggerMessageToExceptionActor(CountLimitedExponentialWaitRetryConfig.builder()
                .maxAttempts(MAX_RETRY_COUNT)
                .waitTimeInMillis(100)
                .maxWaitTimeInMillis(500)
                .multipier(1.1)
                .build());
        assertEquals(MAX_RETRY_COUNT, counter.get());
    }

    @Test
    void testCountLimitedFixedWaitRetryConfig() {
        val counter = triggerMessageToExceptionActor(CountLimitedFixedWaitRetryConfig.builder()
                .maxAttempts(MAX_RETRY_COUNT)
                .waitTimeInMillis(100)
                .build());
        assertEquals(MAX_RETRY_COUNT, counter.get());

    }

    @Test
    void testCountLimitedRandomWaitRetryConfig() {
        val counter = triggerMessageToExceptionActor(CountLimitedRandomWaitRetryConfig.builder()
                .maxAttempts(MAX_RETRY_COUNT)
                .minWaitTimeInMillis(100)
                .maxWaitTimeInMillis(500)
                .build());
        assertEquals(MAX_RETRY_COUNT, counter.get());
    }

    @Test
    void testTimeLimitedExponentialWaitRetryConfig() {
        val s = Stopwatch.createStarted();
        triggerMessageToExceptionActor(TimeLimitedExponentialWaitRetryConfig.builder()
                .maxTimeInMillis(MAX_RETRY_TIME)
                .waitTimeInMillis(500)
                .maxWaitTimeInMillis(1_000)
                .multipier(2.0)
                .build());
        val elapsedTime = s.elapsed().toMillis();
        assertTrue(elapsedTime > MAX_RETRY_TIME);
    }

    @Test
    void testTimeLimitedFixedWaitRetryConfig() {
        val s = Stopwatch.createStarted();
        triggerMessageToExceptionActor(TimeLimitedFixedWaitRetryConfig.builder()
                .maxTimeInMillis(MAX_RETRY_TIME)
                .waitTimeInMillis(1_000)
                .build());
        val elapsedTime = s.elapsed().toMillis();
        assertTrue(elapsedTime > MAX_RETRY_TIME);
    }

    @Test
    void testTimeLimitedRandomWaitRetryConfig() {
        val s = Stopwatch.createStarted();
        triggerMessageToExceptionActor(TimeLimitedRandomWaitRetryConfig.builder()
                .maxTimeInMillis(MAX_RETRY_TIME)
                .minWaiTimeInMillis(500)
                .maxWaitTimeInMillis(1_000)
                .build());
        val elapsedTime = s.elapsed().toMillis();
        assertTrue(elapsedTime > MAX_RETRY_TIME);
    }

    @SneakyThrows
    AtomicInteger triggerMessageToExceptionActor(RetryConfig retryConfig) {
        val counter = new AtomicInteger();
        val sideline = new AtomicBoolean();
        val tc = Executors.newFixedThreadPool(TestUtil.DEFAULT_THREADPOOL_SIZE);
        try (val actorSystem = TestUtil.actorSystem(tc)) {
            val highLevelActorConfig = HighLevelActorConfig.builder()
                    .partitions(Constants.SINGLE_PARTITION)
                    .executorName(TestUtil.GLOBAL_EXECUTOR_SERVICE_GROUP)
                    .retryConfig(retryConfig)
                    .exceptionHandlerConfig(new SidelineConfig())
                    .build();
            val actor = TestUtil.allExceptionActor(counter, sideline,
                    highLevelActorConfig, actorSystem);
            actor.publish(new TestIntMessage(1));
            Awaitility.await()
                    .timeout(Duration.ofMinutes(1))
                    .catchUncaughtExceptions()
                    .until(actor::isEmpty);
            return counter;
        }
    }

}

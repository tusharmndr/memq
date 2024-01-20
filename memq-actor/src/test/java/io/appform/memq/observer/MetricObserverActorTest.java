package io.appform.memq.observer;

import com.codahale.metrics.Meter;
import io.appform.memq.Constants;
import io.appform.memq.actor.ActorOperation;
import io.appform.memq.helper.TestUtil;
import io.appform.memq.helper.message.TestIntMessage;
import lombok.val;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MetricObserverActorTest {

    @Test
    void testMetrics() {
        val tc = Executors.newFixedThreadPool(TestUtil.DEFAULT_THREADPOOL_SIZE);
        try {
            val metricPrefix = "actor." + TestUtil.HighLevelActorType.EXCEPTION_ACTOR.name() + ".";
            val counter = new AtomicInteger();
            val sideline = new AtomicBoolean();
            val actorConfig = TestUtil.noRetryActorConfig(Constants.SINGLE_PARTITION);
            val actorSystem = TestUtil.actorSystem(tc);
            val actor = TestUtil.allExceptionActor(counter, sideline, actorConfig, actorSystem);
            actor.publish(new TestIntMessage(1));
            Awaitility.await()
                    .timeout(Duration.ofMinutes(1))
                    .catchUncaughtExceptions()
                    .until(() -> counter.get() == 1);
            val metrics = actorSystem.metricRegistry().getMetrics();
            assertEquals(13, metrics.size());
            assertEquals(1, ((Meter) metrics.get(metricPrefix + ActorOperation.PUBLISH.name() + ".total")).getCount());
            assertEquals(1, ((Meter) metrics.get(metricPrefix + ActorOperation.HANDLE_EXCEPTION.name() + ".total")).getCount());
            assertEquals(1, ((Meter) metrics.get(metricPrefix + ActorOperation.CONSUME.name() + ".total")).getCount());
        } finally {
            tc.shutdownNow();
        }
    }

    @Test
    void testNoMetrics() {
        val tc = Executors.newFixedThreadPool(TestUtil.DEFAULT_THREADPOOL_SIZE);
        try {
            val counter = new AtomicInteger();
            val sideline = new AtomicBoolean();
            val actorConfig = TestUtil.noRetryActorConfig(Constants.SINGLE_PARTITION);
            actorConfig.setMetricDisabled(true);
            val actorSystem = TestUtil.actorSystem(tc);
            val actor = TestUtil.allExceptionActor(counter, sideline, actorConfig, actorSystem);
            actor.publish(new TestIntMessage(1));
            Awaitility.await()
                    .timeout(Duration.ofMinutes(1))
                    .catchUncaughtExceptions()
                    .until(() -> counter.get() == 1);
            val metrics = actorSystem.metricRegistry().getMetrics();
            assertEquals(0, metrics.size());
        } finally{
                tc.shutdownNow();
            }
        }
    }

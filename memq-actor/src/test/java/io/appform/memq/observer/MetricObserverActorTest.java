package io.appform.memq.observer;

import com.codahale.metrics.Meter;
import io.appform.memq.ActorSystem;
import io.appform.memq.Constants;
import io.appform.memq.MemQTestExtension;
import io.appform.memq.actor.ActorOperation;
import io.appform.memq.helper.TestUtil;
import io.appform.memq.helper.message.TestIntMessage;
import lombok.SneakyThrows;
import lombok.val;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

@ExtendWith(MemQTestExtension.class)
class MetricObserverActorTest {

    @Test
    @SneakyThrows
    void testMetrics(ActorSystem actorSystem) {
            val metricPrefix = "actor." + TestUtil.HighLevelActorType.EXCEPTION_ACTOR.name() + ".";
            val counter = new AtomicInteger();
            val sideline = new AtomicBoolean();
            val actorConfig = TestUtil.noRetryActorConfig(Constants.SINGLE_PARTITION, false);
            val actor = TestUtil.allExceptionActor(counter, sideline,
                    actorConfig, actorSystem);
            actor.publish(new TestIntMessage(1));
            Awaitility.await()
                    .timeout(Duration.ofMinutes(1))
                    .catchUncaughtExceptions()
                    .until(actor::isEmpty); //Wait until all messages are processed
            val metrics = actorSystem.metricRegistry().getMetrics();
            assertEquals(16, metrics.size());
            assertEquals(1, ((Meter) metrics.get(metricPrefix + ActorOperation.PUBLISH.name() + ".total")).getCount());
            assertEquals(1, ((Meter) metrics.get(metricPrefix + ActorOperation.HANDLE_EXCEPTION.name() + ".total")).getCount());
            assertEquals(1, ((Meter) metrics.get(metricPrefix + ActorOperation.CONSUME.name() + ".total")).getCount());
    }

    @Test
    @SneakyThrows
    void testNoMetrics(ActorSystem actorSystem) {
        val counter = new AtomicInteger();
        val sideline = new AtomicBoolean();
        val actorConfig = TestUtil.noRetryActorConfig(Constants.SINGLE_PARTITION, true);
        val actor = TestUtil.allExceptionActor(counter, sideline,
                                               actorConfig, actorSystem);
        actor.publish(new TestIntMessage(1));

        val metrics = actorSystem.metricRegistry().getMetrics();
        assertEquals(0, metrics.size());
    }
}

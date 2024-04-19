package io.appform.memq.observer;

import com.codahale.metrics.Meter;
import io.appform.memq.ActorSystem;
import io.appform.memq.Constants;
import io.appform.memq.MemQTestExtension;
import io.appform.memq.actor.ActorOperation;
import io.appform.memq.helper.TestUtil;
import io.appform.memq.helper.message.TestIntMessage;
import io.appform.memq.observer.impl.ThrottleActorObserver;
import lombok.SneakyThrows;
import lombok.val;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MemQTestExtension.class)
public class ThrottleActorObserverTest {

    @Test
    @SneakyThrows
    void testThrottleActor(ActorSystem actorSystem) {
        val metricPrefix = "actor." + TestUtil.HighLevelActorType.BLOCKING_ACTOR.name() + ".";
        val counter = new AtomicInteger();
        val sideline = new AtomicBoolean();
        val blockConsume = new AtomicBoolean(true);
        val actorConfig = TestUtil.noRetryActorConfig(Constants.SINGLE_PARTITION, false);
        val singleMessageThrottleActorObserver = new ThrottleActorObserver(1);
        val actor = TestUtil.blockingActor(counter, sideline, blockConsume,
                actorConfig, actorSystem, List.of(singleMessageThrottleActorObserver));
        val publish = actor.publish(new TestIntMessage(1));
        assertTrue(publish);

        IntStream.range(0, 10).boxed().forEach(i -> {
            val newPublish = actor.publish(new TestIntMessage(i));
            assertFalse(newPublish);
        });

        blockConsume.set(false);
        Awaitility.await()
                .timeout(Duration.ofMinutes(1))
                .catchUncaughtExceptions()
                .until(actor::isEmpty);
        val metrics = actorSystem.metricRegistry().getMetrics();
        assertEquals(11, ((Meter) metrics.get(metricPrefix + ActorOperation.PUBLISH.name() + ".total")).getCount());
        assertEquals(1, ((Meter) metrics.get(metricPrefix + ActorOperation.PUBLISH.name() + ".success")).getCount());
        assertEquals(10, ((Meter)  metrics.get(metricPrefix + ActorOperation.PUBLISH.name() + ".failed")).getCount());

    }
}

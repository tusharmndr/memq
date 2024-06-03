package io.appform.memq;

import com.codahale.metrics.Meter;
import io.appform.memq.actor.ActorOperation;
import io.appform.memq.helper.TestUtil;
import io.appform.memq.helper.message.TestIntMessage;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.time.Duration;
import java.util.List;
import java.util.Random;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Slf4j
@ExtendWith(MemQTestExtension.class)
public class ActorConcurrencyTest {

    @Test
    public void testMaxConcurrency(ActorSystem actorSystem) {
        val concurrency = new Random().nextInt(1,5);
        val metricPrefix = "actor." + TestUtil.HighLevelActorType.BLOCKING_ACTOR.name() + ".";
        val counter = new AtomicInteger();
        val sideline = new AtomicBoolean();
        val blockConsume = new AtomicBoolean(true);
        val actorConfig = TestUtil.noRetryActorConfig(Constants.SINGLE_PARTITION, false,  Long.MAX_VALUE, concurrency);
        val actor = TestUtil.blockingActor(counter, sideline, blockConsume,
                actorConfig, actorSystem, List.of());
        IntStream.range(0, 10).boxed().forEach(i -> {
            val publish = actor.publish(new TestIntMessage(i));
            assertTrue(publish);
        });
        assertEquals(concurrency, actor.inFlight());
        blockConsume.set(false);
        Awaitility.await()
                .timeout(Duration.ofMinutes(1))
                .catchUncaughtExceptions()
                .until(actor::isEmpty);
        val metrics = actorSystem.metricRegistry().getMetrics();
        assertEquals(10, ((Meter) metrics.get(metricPrefix + ActorOperation.PUBLISH.name() + ".total")).getCount());
    }
}

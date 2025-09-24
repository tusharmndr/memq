package io.appform.memq;

import com.codahale.metrics.Meter;
import io.appform.memq.actor.ActorOperation;
import io.appform.memq.actor.DispatcherType;
import io.appform.memq.helper.TestUtil;
import io.appform.memq.helper.message.TestIntMessage;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Slf4j
public class ActorConcurrencyTest {

    @Test
    @SneakyThrows
    public void testMaxConcurrencyAsyncDispactcher() {
        val concurrency = ThreadLocalRandom.current().nextInt(1, 5);
        val metricPrefix = "actor." + TestUtil.HighLevelActorType.BLOCKING_ACTOR.name() + ".";
        val counter = new AtomicInteger();
        val sideline = new AtomicBoolean();
        val blockConsume = new AtomicBoolean(true);
        val actorConfig = TestUtil.noRetryActorConfig(Constants.SINGLE_PARTITION, false, Long.MAX_VALUE, concurrency);
        val tc = Executors.newFixedThreadPool(TestUtil.DEFAULT_THREADPOOL_SIZE);
        try (val actorSystem = TestUtil.actorSystem(tc, DispatcherType.ASYNC_ISOLATED)) {
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

    @Test
    @SneakyThrows
    public void testMaxConcurrencySyncDispatcher() {
        val concurrency = ThreadLocalRandom.current().nextInt(1, 5);
        val metricPrefix = "actor." + TestUtil.HighLevelActorType.BLOCKING_ACTOR.name() + ".";
        val counter = new AtomicInteger();
        val sideline = new AtomicBoolean();
        val blockConsume = new AtomicBoolean(true);
        val actorConfig = TestUtil.noRetryActorConfig(Constants.SINGLE_PARTITION, false, Long.MAX_VALUE, concurrency);
        val tc = Executors.newFixedThreadPool(TestUtil.DEFAULT_THREADPOOL_SIZE);
        try (val actorSystem = TestUtil.actorSystem(tc, DispatcherType.SYNC)) {
            val actor = TestUtil.blockingActor(counter, sideline, blockConsume,
                    actorConfig, actorSystem, List.of());
            val concurrencyBreached = new AtomicInteger(0);
            IntStream.range(0, 10).boxed().forEach(i -> {
                val message = new TestIntMessage(i);
                while (!actor.publish(message)) {
                    log.debug("Publish failed, retrying for message:{}", message);
                    if (actor.inFlight() == actorConfig.getMaxConcurrencyPerPartition()) {
                        log.debug("Unlocking consume as max currency is achieved while publishing message:{}", message);
                        concurrencyBreached.incrementAndGet();
                        blockConsume.set(false);
                    }
                }
                if (!blockConsume.get()) {
                    blockConsume.set(true);
                }
            });
            assertTrue(concurrencyBreached.get() > 0);
            blockConsume.set(false);
            Awaitility.await()
                    .timeout(Duration.ofMinutes(1))
                    .catchUncaughtExceptions()
                    .until(actor::isEmpty);
            val metrics = actorSystem.metricRegistry().getMetrics();
            assertEquals(10, ((Meter) metrics.get(metricPrefix + ActorOperation.PUBLISH.name() + ".success")).getCount());
        }
    }
}

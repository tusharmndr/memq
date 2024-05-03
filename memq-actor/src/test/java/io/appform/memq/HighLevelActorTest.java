package io.appform.memq;

import com.codahale.metrics.Meter;
import com.google.common.base.Stopwatch;
import io.appform.memq.actor.ActorOperation;
import io.appform.memq.helper.message.TestIntMessage;
import io.appform.memq.helper.TestUtil;
import io.appform.memq.actor.MessageMeta;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;

@Slf4j
@ExtendWith(MemQTestExtension.class)
class HighLevelActorTest {

    enum HighLevelActorType {
        ADDER,
    }

    static final int THREAD_POOL_SIZE = 10;

    @Test
    @SneakyThrows
    void testSuccessSinglePartition(ActorSystem actorSystem) {
        testSuccess(1, actorSystem);
    }

    @Test
    @SneakyThrows
    void testSuccessMultiPartition(ActorSystem actorSystem) {
        testSuccess(4, actorSystem);
    }

    @Test
    @SneakyThrows
    void testBoundedMailboxActorTest(ActorSystem actorSystem) {
        val metricPrefix = "actor." + TestUtil.HighLevelActorType.BLOCKING_ACTOR.name() + ".";
        val counter = new AtomicInteger();
        val sideline = new AtomicBoolean();
        val blockConsume = new AtomicBoolean(true);
        val actorConfig = TestUtil.noRetryActorConfig(Constants.SINGLE_PARTITION, false,
                1L);
        val actor = TestUtil.blockingActor(counter, sideline, blockConsume,
                actorConfig, actorSystem, List.of());
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

    @SneakyThrows
    void testSuccess(int partition, ActorSystem actorSystem) {
        val sum = new AtomicInteger(0);
        val tp = Executors.newFixedThreadPool(THREAD_POOL_SIZE);
        try {
            val a = adder(sum, partition, actorSystem);
            val s = Stopwatch.createStarted();
            IntStream.rangeClosed(1, 10)
                    .forEach(i -> IntStream.rangeClosed(1, 1000).forEach(j -> tp.submit(() -> a.publish(new TestIntMessage(1)))));
            Awaitility.await()
                    .timeout(Duration.ofMinutes(1))
                    .catchUncaughtExceptions()
                    .until(() -> sum.get() == 10_000);
            log.info("Test took {} ms",
                    s.elapsed().toMillis());
            assertEquals(10_000, sum.get());
        } finally {
            tp.shutdownNow();
        }
    }

    HighLevelActor<HighLevelActorType, TestIntMessage> adder(final AtomicInteger sum, int partition, ActorSystem actorSystem) {
        return new HighLevelActor<>(HighLevelActorType.ADDER,
                TestUtil.noRetryActorConfig(partition),
                actorSystem,
                message -> Math.absExact(message.id().hashCode()) % partition
        ) {
            @Override
            protected boolean handle(TestIntMessage message, MessageMeta messageMeta) {
                sum.addAndGet(message.getValue());
                return true;
            }
        };
    }


}

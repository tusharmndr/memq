package io.appform.memq;

import com.google.common.base.Stopwatch;
import io.appform.memq.helper.message.TestIntMessage;
import io.appform.memq.helper.TestUtil;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;

@Slf4j
class HighLevelActorTest {

    enum HighLevelActorType {
        ADDER,
    }

    @Test
    @SneakyThrows
    void testSuccessSinglePartition() {
        testSuccess(1);
    }

    @Test
    @SneakyThrows
    void testSuccessMultiPartition() {
        testSuccess(4);
    }

    @SneakyThrows
    void testSuccess(int partition) {
        val sum = new AtomicInteger(0);
        val tp = Executors.newFixedThreadPool(TestUtil.DEFAULT_THREADPOOL_SIZE);
        val tc = Executors.newFixedThreadPool(TestUtil.DEFAULT_THREADPOOL_SIZE);
        try {
            val a = adder(sum, partition, tc);
            val s = Stopwatch.createStarted();
            IntStream.rangeClosed(1, 10)
                    .forEach(i -> IntStream.rangeClosed(1, 1000).forEach(j -> tp.submit(() -> a.publish(new TestIntMessage(1)))));
            Awaitility.await()
                    .timeout(Duration.ofMinutes(1))
                    .catchUncaughtExceptions()
                    .until(a::isEmpty);
            log.info("Test took {} ms",
                    s.elapsed().toMillis());
            assertEquals(10_000, sum.get());
        } finally {
            tp.shutdownNow();
            tc.shutdownNow();
        }

    }

    HighLevelActor adder(final AtomicInteger sum, int partition, ExecutorService tp) throws Exception {
        return new HighLevelActor<HighLevelActorType, TestIntMessage>(HighLevelActorType.ADDER,
                TestUtil.noRetryActorConfig(partition),
                TestUtil.actorSystem(tp),
                message -> Math.absExact(message.id().hashCode()) % partition
        ) {
            @Override
            protected boolean handle(TestIntMessage message) {
                sum.addAndGet(message.getValue());
                return true;
            }
        };
    }


}

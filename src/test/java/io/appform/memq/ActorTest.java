package io.appform.memq;

import com.google.common.base.Stopwatch;
import lombok.SneakyThrows;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 *
 */
@Slf4j
class ActorTest {

    @Value
    private static class TestMessage implements Message {
        int value;
        String id = UUID.randomUUID().toString();
        @Override
        public String id() {
            return id;
        }
    }

    @Test
    @SneakyThrows
    void testSuccess() {
        val sum = new AtomicInteger(0);
        val tp = Executors.newFixedThreadPool(100);
        try(val a = adder(sum)) {
            a.start();
            val s = Stopwatch.createStarted();
            IntStream.rangeClosed(1, 10)
                    .forEach(i -> IntStream.rangeClosed(1, 1000).forEach(j -> tp.submit(() -> assertTrue(a.publish(new TestMessage(1))))));
            Awaitility.await()
                    .forever()
                    .catchUncaughtExceptions()
                    .until(a::isEmpty);
            log.info("Test took {} ms", s.elapsed().toMillis());
            assertEquals(10_000, sum.get());
        }
        finally {
            tp.shutdownNow();
        }
    }

    private static Actor<TestMessage> adder(final AtomicInteger sum) {
        return new Actor<>(Executors.newFixedThreadPool(1024), Set.of()) {

            @Override
            public String name() {
                return "Adder";
            }

            @Override
            protected boolean handleMessage(TestMessage message) {
                sum.addAndGet(message.getValue());
                return true;
            }
        };
    }

}
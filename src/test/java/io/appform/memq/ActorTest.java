package io.appform.memq;

import com.google.common.base.Stopwatch;
import io.appform.memq.actor.Actor;
import io.appform.memq.actor.Message;
import io.appform.memq.exceptionhandler.config.DropConfig;
import io.appform.memq.exceptionhandler.handlers.MessageDropHandler;
import io.appform.memq.retry.config.NoRetryConfig;
import io.appform.memq.retry.impl.NoRetryStrategy;
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
    void testSuccessSinglePartition() {
        testSuccess(1);
    }

    @Test
    @SneakyThrows
    void testSuccessMultiPartition() {
        testSuccess(4);
    }


    @SneakyThrows
    private void testSuccess(int partition) {
        val sum = new AtomicInteger(0);
        val tp = Executors.newFixedThreadPool(100);
        try (val a = adder(sum, partition)) {
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
        } finally {
            tp.shutdownNow();
        }
    }


    private static Actor<TestMessage> adder(final AtomicInteger sum, int partition) {
        return new Actor<>(Executors.newFixedThreadPool(1024),
                new NoRetryStrategy(new NoRetryConfig()),
                new MessageDropHandler(new DropConfig()),
                Set.of(),
                partition, message -> Math.absExact(message.id.hashCode()) % partition) {

            @Override
            public String name() {
                return "Adder";
            }

            @Override
            protected boolean handleMessage(TestMessage message) {
                log.info("Received message:{}", message);
                sum.addAndGet(message.getValue());
                return true;
            }

            @Override
            protected void sidelineMessage(TestMessage record) {
            }
        };
    }

}
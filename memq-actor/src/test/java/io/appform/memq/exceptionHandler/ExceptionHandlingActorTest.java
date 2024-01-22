package io.appform.memq.exceptionHandler;

import io.appform.memq.Constants;
import io.appform.memq.exceptionhandler.config.DropConfig;
import io.appform.memq.exceptionhandler.config.ExceptionHandlerConfig;
import io.appform.memq.exceptionhandler.config.SidelineConfig;
import io.appform.memq.helper.message.TestIntMessage;
import io.appform.memq.helper.TestUtil;
import lombok.SneakyThrows;
import lombok.val;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class ExceptionHandlingActorTest {

    @Test
    void testSidelineExceptionConfig() {
        val sideline = triggerMessageToExceptionActor(new SidelineConfig());
        assertTrue(sideline.get());
    }

    @Test
    void testDropExceptionConfig() {
        val sideline = triggerMessageToExceptionActor(new DropConfig());
        assertFalse(sideline.get());
    }

    @SneakyThrows
    AtomicBoolean triggerMessageToExceptionActor(ExceptionHandlerConfig exceptionHandlerConfig) {
        val counter = new AtomicInteger();
        val sideline = new AtomicBoolean();
        val tc = Executors.newFixedThreadPool(TestUtil.DEFAULT_THREADPOOL_SIZE);
        try (val actorSystem = TestUtil.actorSystem(tc)) {
            val highLevelActorConfig = TestUtil.noRetryActorConfig(Constants.SINGLE_PARTITION);
            highLevelActorConfig.setExceptionHandlerConfig(exceptionHandlerConfig);
            val actor = TestUtil.allExceptionActor(counter, sideline,
                    highLevelActorConfig, actorSystem);
            actor.publish(new TestIntMessage(1));
            Awaitility.await()
                    .timeout(Duration.ofMinutes(1))
                    .catchUncaughtExceptions()
                    .until(actor::isEmpty);
            return sideline;
        }
    }
}

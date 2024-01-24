package io.appform.memq.exceptionHandler;

import io.appform.memq.ActorSystem;
import io.appform.memq.Constants;
import io.appform.memq.MemQTestExtension;
import io.appform.memq.exceptionhandler.config.DropConfig;
import io.appform.memq.exceptionhandler.config.ExceptionHandlerConfig;
import io.appform.memq.exceptionhandler.config.SidelineConfig;
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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(MemQTestExtension.class)
class ExceptionHandlingActorTest {

    @Test
    void testSidelineExceptionConfig(ActorSystem actorSystem) {
        val sideline = triggerMessageToExceptionActor(new SidelineConfig(), actorSystem);
        assertTrue(sideline.get());
    }

    @Test
    void testDropExceptionConfig(ActorSystem actorSystem) {
        val sideline = triggerMessageToExceptionActor(new DropConfig(), actorSystem);
        assertFalse(sideline.get());
    }

    @SneakyThrows
    AtomicBoolean triggerMessageToExceptionActor(
            ExceptionHandlerConfig exceptionHandlerConfig,
            ActorSystem actorSystem) {
        val counter = new AtomicInteger();
        val sideline = new AtomicBoolean();
        val highLevelActorConfig = TestUtil.noRetryActorConfig(Constants.SINGLE_PARTITION, exceptionHandlerConfig);
        val actor
                = TestUtil.allExceptionActor(counter, sideline, highLevelActorConfig, actorSystem);
        actor.publish(new TestIntMessage(1));
        Awaitility.await()
                .timeout(Duration.ofMinutes(1))
                .catchUncaughtExceptions()
                .until(() -> counter.get() > 0);
        return sideline;
    }
}


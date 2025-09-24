package io.appform.memq;

import io.appform.memq.helper.TestUtil;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.extension.*;

import java.time.Duration;
import java.util.concurrent.Executors;

/**
 *
 */
public class MemQTestExtension implements BeforeEachCallback, AfterEachCallback, ParameterResolver {
    private ActorSystem actorSystem;

    @Override
    public void afterEach(ExtensionContext extensionContext) throws Exception {
        actorSystem.close();
        Awaitility.await()
                .timeout(Duration.ofMinutes(1))
                .catchUncaughtExceptions()
                .until(() -> !actorSystem.isRunning());
    }

    @Override
    public void beforeEach(ExtensionContext extensionContext) throws Exception {
        actorSystem = TestUtil.actorSystem(Executors.newFixedThreadPool(TestUtil.DEFAULT_THREADPOOL_SIZE),
                TestUtil.DEFAULT_DISPATCHER);
    }

    @Override
    public boolean supportsParameter(
            ParameterContext parameterContext,
            ExtensionContext extensionContext) throws ParameterResolutionException {
        return parameterContext.getParameter().getType().equals(ActorSystem.class);
    }

    @Override
    public Object resolveParameter(
            ParameterContext parameterContext,
            ExtensionContext extensionContext) throws ParameterResolutionException {
        return actorSystem;
    }
}

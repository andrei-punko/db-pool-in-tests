package by.andd3dfx.db.pool;

import by.andd3dfx.db.metrics.Metrics;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.springframework.test.context.junit.jupiter.SpringExtension;

/**
 * During test execution: log method start, duration, pool metrics after each method,
 * and database creation stats after the test class.
 */
@Slf4j
public class DatabasePoolTimeLoggingExtension implements BeforeEachCallback, AfterEachCallback, AfterAllCallback {

    private static final ExtensionContext.Namespace NAMESPACE =
            ExtensionContext.Namespace.create(DatabasePoolTimeLoggingExtension.class);

    @Override
    public void beforeEach(ExtensionContext context) {
        context.getStore(NAMESPACE).put(context.getUniqueId(), System.nanoTime());
        log.info("[db-pool] test starting: {}", context.getDisplayName());
    }

    @Override
    public void afterEach(ExtensionContext context) throws Exception {
        Long startNanos = context.getStore(NAMESPACE).remove(context.getUniqueId(), Long.class);
        if (startNanos != null) {
            long elapsedMs = (System.nanoTime() - startNanos) / 1_000_000L;
            log.info("[db-pool] test finished in {} ms: {}", elapsedMs, context.getDisplayName());
        }
        SpringExtension.getApplicationContext(context).getBean(Metrics.class)
                .logPoolWaitStats(context.getDisplayName());
    }

    @Override
    public void afterAll(ExtensionContext context) throws Exception {
        SpringExtension.getApplicationContext(context).getBean(Metrics.class)
                .logDbCreationStats(context.getDisplayName());
    }
}

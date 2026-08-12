package by.andd3dfx.db;

import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.springframework.test.context.junit.jupiter.SpringExtension;

/**
 * Prints DB pool wait metrics after each integration test method.
 * <p>
 * Prints DB creation metrics after all integration test in the current container.
 */
public class DatabasePoolTimeLoggingExtension implements AfterEachCallback, AfterAllCallback {

    @Override
    public void afterEach(ExtensionContext context) throws Exception {
        SpringExtension.getApplicationContext(context).getBean(Metrics.class)
                .logPoolWaitStats(context.getDisplayName());
    }

    @Override
    public void afterAll(ExtensionContext context) throws Exception {
        SpringExtension.getApplicationContext(context).getBean(Metrics.class)
                .logDbCreationStats(context.getDisplayName());
    }
}

package by.andd3dfx.db.pool;

import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.SmartLifecycle;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Manages a pool of pre-created test databases for fast test execution.
 * Each test gets a fresh database, which is dropped after the test completes.
 * <p>
 * <b>Important:</b> This class is designed for SEQUENTIAL tests execution only.
 * Parallel tests execution requires tracking which database belongs to which test thread.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DatabasePoolLifecycleService implements SmartLifecycle {

    private final DatabasePool databasePool;
    private final ApplicationEventPublisher applicationEventPublisher;
    private final AtomicBoolean shutdownTriggered = new AtomicBoolean(false);
    private final AtomicBoolean lifecycleRunning = new AtomicBoolean(false);

    @EventListener(ContextRefreshedEvent.class)
    public void init() {
        databasePool.init();

        // Mark running so DefaultLifecycleProcessor invokes stop() on context close even if
        // SmartLifecycle.start() was skipped (e.g. bean created after initial lifecycle onRefresh).
        lifecycleRunning.set(true);

        // Fallback when the cached Spring test context is closed on JVM exit (Surefire may not flush late logs).
        // Multiple refreshes may register several hooks; but triggerShutdown is idempotent - so we protected vs multiple calls of it.
        Runtime.getRuntime().addShutdownHook(new Thread(() -> triggerShutdown("JvmShutdownHook"), "db-pool-jvm-shutdown"));
    }

    @Override
    public void start() {
        lifecycleRunning.compareAndSet(false, true);
    }

    @Override
    public void stop(Runnable callback) {
        try {
            triggerShutdown("SmartLifecycle");
        } finally {
            lifecycleRunning.set(false);
            callback.run();
        }
    }

    @Override
    public void stop() {
        triggerShutdown("SmartLifecycle");
        lifecycleRunning.set(false);
    }

    @Override
    public boolean isRunning() {
        return lifecycleRunning.get();
    }

    @Order(Ordered.HIGHEST_PRECEDENCE)
    @EventListener(ContextClosedEvent.class)
    public void onContextClosed() {
        triggerShutdown("ContextClosedEvent");
    }

    @PreDestroy
    public void destroy() {
        triggerShutdown("PreDestroy");
    }

    public void releaseCurrentDatabase() {
        databasePool.releaseCurrentDatabase();
    }

    private void triggerShutdown(String source) {
        if (shutdownTriggered.compareAndSet(false, true)) {
            log.warn("Shutting down DatabasePool from {}", source);
            databasePool.shutdown(applicationEventPublisher);
        }
    }
}

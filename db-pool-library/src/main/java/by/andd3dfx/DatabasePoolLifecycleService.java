package by.andd3dfx;

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
 * Инициализация {@link DatabasePool} при подъёме контекста и корректное завершение при остановке.
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

        lifecycleRunning.set(true);

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

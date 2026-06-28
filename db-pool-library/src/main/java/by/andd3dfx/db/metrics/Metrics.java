package by.andd3dfx.db.metrics;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.time.DurationFormatUtils;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * <pre>
 * Collect & log:
 * - Amount of times when test could not get prepared DB from pool (and because of that new DB creation was initiated)
 * - DB creation statistics
 * - DB pool wait statistics
 * </pre>
 */
@Slf4j
@Component
public final class Metrics {

    private static final String DURATION_PATTERN = "mm:ss.SSS";

    private final AtomicInteger emptyPoolEventsCounter = new AtomicInteger(0);
    private final TimeCounter creationTimeCounter = new TimeCounter();
    private final TimeCounter poolWaitTimeCounter = new TimeCounter();

    public void incrementEmptyPoolEventsCounter() {
        emptyPoolEventsCounter.incrementAndGet();
    }

    public void recordDatabaseCreationTime(long timeMs) {
        creationTimeCounter.add(timeMs);
    }

    public void recordPoolWaitTime(long timeMs) {
        poolWaitTimeCounter.add(timeMs);
    }

    public void logPoolWaitStats(String testName) {
        TimeCounter.Snapshot snapshot = poolWaitTimeCounter.snapshot();
        log.info("[db-pool-stats] {}, emptyPoolEventsCount={}, waitCount={}, total={}, avg={}, max={}",
                testName,
                emptyPoolEventsCounter.get(),
                snapshot.count(),
                formatDuration(snapshot.totalTime()),
                formatDuration(snapshot.average()),
                formatDuration(snapshot.maxTime()));
    }

    public void logDbCreationStats(String testClassName) {
        TimeCounter.Snapshot snapshot = creationTimeCounter.snapshot();
        log.info("[db-creation-stats] {}, count={}, total={}, avg={}, max={}",
                testClassName,
                snapshot.count(),
                formatDuration(snapshot.totalTime()),
                formatDuration(snapshot.average()),
                formatDuration(snapshot.maxTime()));
    }

    private static String formatDuration(long milliseconds) {
        return DurationFormatUtils.formatDuration(milliseconds, DURATION_PATTERN);
    }
}

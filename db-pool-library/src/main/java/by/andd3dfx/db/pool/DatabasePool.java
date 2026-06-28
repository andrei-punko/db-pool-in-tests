package by.andd3dfx.db.pool;

import by.andd3dfx.db.template.DatabaseTemplateService;
import by.andd3dfx.db.model.PreparedDatabase;
import by.andd3dfx.db.datasource.RoutingDataSource;
import by.andd3dfx.db.metrics.Metrics;
import by.andd3dfx.event.FlushOnSystemShutdownEvent;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.RandomStringUtils;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.BlockingDeque;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Maintains a pool of pre-created test databases to reduce startup time for tests.
 * <p>
 * The pool creates databases in background, hands out one database per test flow,
 * and performs deferred cleanup and final shutdown cleanup.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DatabasePool {

    /**
     * Maximum number of pre-created databases to keep ready in the pool.
     */
    private static final int MAX_POOLED_DBS = 6;

    /**
     * Number of threads for creating new databases in parallel.
     */
    private static final int DB_CREATION_THREAD_POOL_SIZE = Math.max(2, MAX_POOLED_DBS / 4);

    /**
     * Maximum databases to queue for deletion before forcing cleanup.
     */
    private static final int MAX_PENDING_FOR_DELETION_QUEUE_SIZE = MAX_POOLED_DBS / 2;

    private final DatabaseTemplateService databaseTemplateService;
    private final RoutingDataSource routingDataSource;
    private final Metrics metrics;

    private final AtomicBoolean isInitialized = new AtomicBoolean(false);
    private final AtomicReference<String> dbNameAssignedToCurrentTestFlow = new AtomicReference<>();
    private final BlockingDeque<PreparedDatabase> readyToUseDatabasesPool = new LinkedBlockingDeque<>();
    private final AtomicInteger databasesBeingCreatedCounter = new AtomicInteger(0);
    private final Deque<String> pendingForDeletionDbNamesQueue = new ConcurrentLinkedDeque<>();

    private final ScheduledExecutorService monitorExecutor = Executors.newSingleThreadScheduledExecutor(
            threadFactory("TestDbPool-monitor-"));
    private final ExecutorService databaseCreationExecutor = Executors.newFixedThreadPool(
            DB_CREATION_THREAD_POOL_SIZE, threadFactory("TestDbPool-creator-"));
    private final ExecutorService cleanupExecutor = Executors.newFixedThreadPool(
            DB_CREATION_THREAD_POOL_SIZE, threadFactory("TestDbPool-cleanup-"));

    public void init() {
        if (isInitialized.get()) {
            return;
        }

        log.info("Initializing DatabasePool...");
        databaseTemplateService.createAndPopulateTemplate();
        startBackgroundDatabaseCreation();
        log.info("DatabasePool initialized successfully");

        isInitialized.set(true);
    }

    public boolean isInitialized() {
        return isInitialized.get();
    }

    public String getNextDatabaseName() {
        if (!isInitialized.get()) {
            return RoutingDataSource.SYSTEM_DB_KEY;
        }

        String currentDbName = dbNameAssignedToCurrentTestFlow.get();
        if (currentDbName != null) {
            return currentDbName;
        }

        PreparedDatabase database = acquireDatabaseFromPool();
        return database.getRequiredDatabaseName();
    }

    public void shutdown(ApplicationEventPublisher eventPublisher) {
        log.warn("DatabasePool is shutting down...");
        try {
            initiateExecutorShutdown("monitorExecutor", monitorExecutor);
            initiateExecutorShutdown("databaseCreationExecutor", databaseCreationExecutor);

            eventPublisher.publishEvent(new FlushOnSystemShutdownEvent());

            awaitExecutorTermination("monitorExecutor", monitorExecutor);
            awaitExecutorTermination("databaseCreationExecutor", databaseCreationExecutor);

            logPendingCreationsOnShutdown();

            // TODO most probably, we don't need cleanup at all
            cleanupAllDatabases();
        } catch (Exception e) {
            log.error("Error during DatabasePool shutdown", e);
        }
    }

    public void releaseCurrentDatabase() {
        String currentDbName = dbNameAssignedToCurrentTestFlow.get();
        if (currentDbName != null) {
            pendingForDeletionDbNamesQueue.add(currentDbName);
            routingDataSource.removeDataSource(currentDbName);
        }
        dbNameAssignedToCurrentTestFlow.set(null);
        performLazyCleanup();
    }

    private void startBackgroundDatabaseCreation() {
        monitorExecutor.scheduleWithFixedDelay(this::monitorAndScheduleDatabaseCreation, 0L, 100L, TimeUnit.MILLISECONDS);
    }

    @SneakyThrows
    private PreparedDatabase acquireDatabaseFromPool() {
        boolean poolWasEmpty = readyToUseDatabasesPool.isEmpty();
        long waitStartTime = System.currentTimeMillis();

        if (poolWasEmpty) {
            triggerDatabaseCreation();
            log.warn("Database pool is empty, waiting for a database to become available...");
            metrics.incrementEmptyPoolEventsCounter();
        }

        PreparedDatabase database = readyToUseDatabasesPool.take();

        if (poolWasEmpty) {
            long waitTime = System.currentTimeMillis() - waitStartTime;
            metrics.recordPoolWaitTime(waitTime);
            log.info("Database became available after {} ms", waitTime);
        }

        database.checkForError();
        dbNameAssignedToCurrentTestFlow.set(database.getRequiredDatabaseName());
        return database;
    }

    private void triggerDatabaseCreation() {
        if (!monitorExecutor.isShutdown()) {
            monitorExecutor.execute(this::monitorAndScheduleDatabaseCreation);
        }
    }

    private void dropDatabase(String dbToDelete) {
        databaseTemplateService.dropDatabase(dbToDelete);
    }

    private void dropAllPendingForDeletionDatabases() {
        String dbToDelete;
        while ((dbToDelete = pendingForDeletionDbNamesQueue.pollFirst()) != null) {
            dropDatabase(dbToDelete);
        }
    }

    private void waitForDeletions(List<CompletableFuture<Void>> futures) {
        try {
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).get(30, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.warn("Some databases failed to drop or timed out: {}", e.getMessage());
        }
    }

    private void monitorAndScheduleDatabaseCreation() {
        if (databaseCreationExecutor.isShutdown()) {
            return;
        }

        int currentPoolSize = readyToUseDatabasesPool.size();
        int beingCreated = databasesBeingCreatedCounter.get();
        int needed = MAX_POOLED_DBS - currentPoolSize - beingCreated;

        for (int i = 0; i < needed; i++) {
            databasesBeingCreatedCounter.incrementAndGet();
            databaseCreationExecutor.submit(this::createDatabaseTask);
        }
    }

    private void createDatabaseTask() {
        long startTime = System.currentTimeMillis();
        try {
            createAndAddDatabaseToPool(generateTestDatabaseName());
        } finally {
            databasesBeingCreatedCounter.decrementAndGet();
        }
        long elapsedTime = System.currentTimeMillis() - startTime;
        metrics.recordDatabaseCreationTime(elapsedTime);

        log.debug("Database created and added to pool (size={}, time={})", readyToUseDatabasesPool.size(), Duration.ofMillis(elapsedTime));
    }

    private String generateTestDatabaseName() {
        String randomSuffix = RandomStringUtils.insecure().nextAlphanumeric(8).toLowerCase();
        return "test_" + randomSuffix + "_db";
    }

    @SuppressWarnings("checkstyle:IllegalCatch")
    private void createAndAddDatabaseToPool(String newDbName) {
        try {
            log.debug("Creating test database from template: {}", newDbName);

            long startTime = System.currentTimeMillis();
            databaseTemplateService.createFromTemplate(newDbName);
            long elapsedTime = System.currentTimeMillis() - startTime;
            log.debug("Creating test database {} from template took {} ms", newDbName, elapsedTime);

            boolean added = readyToUseDatabasesPool.offer(PreparedDatabase.success(newDbName), 30L, TimeUnit.SECONDS);
            if (!added) {
                log.warn("Failed to add database to pool (queue full): {}", newDbName);
                dropDatabase(newDbName);
            } else {
                log.debug("Successfully added test database to pool: {}", newDbName);
            }
        } catch (Throwable e) {
            log.error("Failed to create test database", e);
            readyToUseDatabasesPool.offer(PreparedDatabase.error(e));
        }
    }

    private static void initiateExecutorShutdown(String name, ExecutorService executor) {
        List<Runnable> remaining = executor.shutdownNow();
        if (!remaining.isEmpty()) {
            log.warn("Executor {} has {} remaining tasks", name, remaining.size());
        }
    }

    private static void awaitExecutorTermination(String name, ExecutorService executor) throws InterruptedException {
        final boolean terminated = executor.awaitTermination(10, TimeUnit.SECONDS);
        if (!terminated) {
            log.warn("Executor {} did not terminate gracefully, still running tasks", name);
        }
    }

    private void logPendingCreationsOnShutdown() {
        int pending = databasesBeingCreatedCounter.get();
        if (pending > 0) {
            log.info("Databases still being created at shutdown: {}", pending);
        }
    }

    private void cleanupAllDatabases() {
        List<CompletableFuture<Void>> deletionFutures = new ArrayList<>();

        dropAllPendingForDeletionDatabases();

        PreparedDatabase prepared;
        while ((prepared = readyToUseDatabasesPool.pollFirst()) != null) {
            if (prepared.isSuccess()) {
                final String dbToDelete = prepared.databaseName();
                deletionFutures.add(CompletableFuture.runAsync(() -> dropDatabase(dbToDelete), cleanupExecutor));
            }
        }
        waitForDeletions(deletionFutures);
        initiateExecutorShutdown("cleanupExecutor", cleanupExecutor);

        databaseTemplateService.dropTemplate();
    }

    private void performLazyCleanup() {
        while (pendingForDeletionDbNamesQueue.size() > MAX_PENDING_FOR_DELETION_QUEUE_SIZE) {
            String dbToDelete = pendingForDeletionDbNamesQueue.pollFirst();
            if (dbToDelete != null) {
                databaseCreationExecutor.submit(() -> dropDatabase(dbToDelete));
            }
        }
    }

    private static ThreadFactory threadFactory(String threadNamePrefix) {
        return runnable -> createThread(runnable, threadNamePrefix);
    }

    private static Thread createThread(Runnable task, String threadNamePrefix) {
        Thread thread = new Thread(task);
        thread.setName(threadNamePrefix + thread.threadId());
        thread.setDaemon(true);
        return thread;
    }
}

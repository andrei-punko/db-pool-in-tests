package by.andd3dfx.testcontainers;

import lombok.SneakyThrows;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.text.WordUtils;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.testcontainers.containers.output.OutputFrame;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ForkJoinTask;
import java.util.concurrent.TimeUnit;

@UtilityClass
@Slf4j
public class ContainersLifecycleSupport {

    // TODO CONST FOR MEMORY LIMIT
    // private static final int POSTGRES_CONTAINER_LIMIT_MEGABYTES = 1024;

    private static final PostgreSQLContainer POSTGRESQL_CONTAINER = PostgresContainerFactory.create()
            .withLogConsumer(ContainersLifecycleSupport::logOutput);

    private static final ForkJoinTask<String> POSTGRESQL_CONTAINER_START_RESULT =
            // Manually start a container. Autostart is too late (after context is started)
            ForkJoinPool.commonPool().submit(ContainersLifecycleSupport::executeStartDbContainer);

    private static void logOutput(OutputFrame outputFrame) {
        if (log.isDebugEnabled()) {
            log.debug("POSTGRES CONSOLE: {}", outputFrame.getUtf8String());
        }
    }

    public static void init() {
        // Container is starting in POSTGRESQL_CONTAINER_START_RESULT
    }

    private static boolean isTestProfile() {
        String property = System.getProperty("spring.profiles.active");
        return property != null && WordUtils.containsAllWords(property, "it");
    }

    public static void postgresqlProperties(DynamicPropertyRegistry registry) {
        if (isTestProfile()) {
            log.warn("postgresqlProperties: Adding properties callbacks...");
            registry.add("spring.datasource.url", () -> {
                waitForDbContainerIsRunning();
                return POSTGRESQL_CONTAINER.getJdbcUrl();
            });
            registry.add("spring.datasource.username", () -> {
                waitForDbContainerIsRunning();
                return POSTGRESQL_CONTAINER.getUsername();
            });
            // Password is not needed since container is configured with TRUST auth
        }
    }

    @SneakyThrows
    private static void waitForDbContainerIsRunning() {
        if (isTestProfile() && !POSTGRESQL_CONTAINER.isRunning()) {
            log.warn("waitForDbContainerIsRunning: waiting for initialization...");
            POSTGRESQL_CONTAINER_START_RESULT.get(5, TimeUnit.MINUTES);
            log.warn("waitForDbContainerIsRunning: waiting finished");
            if (!POSTGRESQL_CONTAINER.isRunning()) {
                throw new IllegalStateException("POSTGRESQL_CONTAINER is not running");
            }
        }
    }

    private static String executeStartDbContainer() {
        String result;
        if (isTestProfile()) {
            log.warn("executeStartDbContainer: starting DB container...");
            POSTGRESQL_CONTAINER.start();
            log.warn("executeStartDbContainer: started");
            result = "STARTED";
        } else {
            result = "SKIPPED";
        }
        return result;
    }

    @SneakyThrows
    public static void stopContainer() {
        if (!POSTGRESQL_CONTAINER.isRunning()) {
            log.warn("stopContainer: POSTGRESQL_CONTAINER is not running");
            return;
        }
        log.warn("stopContainer: Stopping POSTGRESQL_CONTAINER...");
        POSTGRESQL_CONTAINER.stop();
        log.warn("stopContainer: Stopped POSTGRESQL_CONTAINER");
    }

}

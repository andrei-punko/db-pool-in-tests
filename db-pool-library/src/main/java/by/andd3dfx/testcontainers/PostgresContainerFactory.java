package by.andd3dfx.testcontainers;

import org.springframework.util.unit.DataSize;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.util.Collections;

public final class PostgresContainerFactory {
    // Don't modify this declaration. It's used in bitbucket-pipeline to retrieve image version. But you can modify the value (version value)
    public static final String IMAGE = "huntress/postgres-partman:16.8";

    private static final DataSize CONTAINER_MEMORY_LIMIT = DataSize.ofGigabytes(2);
    private static final DataSize CONTAINER_SHARED_MEMORY_SIZE = DataSize.ofMegabytes(256);

    private PostgresContainerFactory() {
    }

    public static PostgreSQLContainer create() {
        DockerImageName dockerImageName = DockerImageName.parse(IMAGE)
                .asCompatibleSubstituteFor("postgres");
        return new PostgreSQLContainer(dockerImageName)
                .withCommand(postgresStartupCommand())
                .withTmpFs(Collections.singletonMap("/var/lib/postgresql/data", "rw,noatime,nodiratime,size=1000m"))
                .withSharedMemorySize(CONTAINER_SHARED_MEMORY_SIZE.toBytes())
                .withCreateContainerCmdModifier(cmd -> cmd.getHostConfig()
                        .withMemory(CONTAINER_MEMORY_LIMIT.toBytes())
                        .withMemorySwap(CONTAINER_MEMORY_LIMIT.toBytes()))
                .withEnv("POSTGRES_HOST_AUTH_METHOD", "trust");
    }

    private static String[] postgresStartupCommand() {
        return new String[]{
                "postgres",
                "-c", "shared_buffers=128MB",
                "-c", "work_mem=4MB",
                "-c", "maintenance_work_mem=64MB",
                "-c", "effective_cache_size=512MB",
                "-c", "autovacuum=off",

                // DEFAULT: on - DISABLED, we don't need any durability
                "-c", "fsync=off",
                // DEFAULT: on - DISABLED, we don't need WAL files for durability
                "-c", "full_page_writes=off",

                // Connection/Performance - Reduced to limit memory usage
                // Each connection uses ~5-10MB, so 30 connections = ~150-300MB overhead
                "-c", "max_connections=30",
                // DEFAULT: on - Skip WAL waits
                "-c", "synchronous_commit=off",

                // Connection optimization - reduce overhead for new connections
                "-c", "ssl=off",
                "-c", "password_encryption=md5",
                "-c", "tcp_keepalives_idle=60",
                "-c", "tcp_keepalives_interval=10",
                "-c", "tcp_keepalives_count=5",

                // Reduces WAL overhead
                "-c", "wal_level=minimal",
                "-c", "max_wal_senders=0",
                "-c", "archive_mode=off",
                "-c", "wal_compression=off",
                "-c", "checkpoint_timeout=1h",
                "-c", "max_wal_size=4GB",

                "-c", "max_parallel_workers=0",
                "-c", "max_parallel_workers_per_gather=0",

                "-c", "commit_delay=0",
                "-c", "commit_siblings=0",
                "-c", "max_worker_processes=4",

                // Lock settings
                "-c", "max_locks_per_transaction=256",
                "-c", "max_pred_locks_per_transaction=256",

                // Temp file limits - prevent disk exhaustion
                "-c", "temp_file_limit=256MB"
        };
    }
}

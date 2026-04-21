package by.andd3dfx.testcontainers;

import lombok.experimental.UtilityClass;
import org.springframework.util.unit.DataSize;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.util.Collections;

@UtilityClass
public final class PostgresContainerFactory {

    public static final String IMAGE = "postgres:16.8";

    private static final DataSize CONTAINER_MEMORY_LIMIT = DataSize.ofGigabytes(2);
    private static final DataSize CONTAINER_SHARED_MEMORY_SIZE = DataSize.ofMegabytes(256);

    public static PostgreSQLContainer create() {
        return new PostgreSQLContainer(IMAGE)
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
                "-c", "fsync=off",
                "-c", "full_page_writes=off",
                "-c", "max_connections=30",
                "-c", "synchronous_commit=off",
                "-c", "ssl=off",
                "-c", "password_encryption=md5",
                "-c", "tcp_keepalives_idle=60",
                "-c", "tcp_keepalives_interval=10",
                "-c", "tcp_keepalives_count=5",
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
                "-c", "max_locks_per_transaction=256",
                "-c", "max_pred_locks_per_transaction=256",
                "-c", "temp_file_limit=256MB"
        };
    }
}

package by.andd3dfx.db;

import com.zaxxer.hikari.HikariDataSource;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.datasource.lookup.AbstractRoutingDataSource;

import javax.sql.DataSource;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Custom RoutingDataSource for testing that allows dynamic switching between multiple DataSources
 * <p>
 * For regular test execution flow it asks {@link DatabasePool} for the next database name.
 * It also supports temporary override via {@link #withCurrentDb(String, Runnable)} for
 * operations that must run against a specific datasource key.
 */
@Slf4j
@AllArgsConstructor
public class RoutingDataSource extends AbstractRoutingDataSource {

    public static final String SYSTEM_DB_KEY = "system";

    private final Map<String, DataSource> dbNameToDataSourceMap = new ConcurrentHashMap<>();
    /**
     * Temporary routing override to specific code block (for example, template initialization flow)
     */
    private final ThreadLocal<String> overriddenDbName = new ThreadLocal<>();
    private final DataSourceFactory dataSourceFactory;
    private final DatabasePool databasePool;

    public DataSource getDataSourceByKey(String dataSourceLookupKey) {
        return dbNameToDataSourceMap.get(dataSourceLookupKey);
    }

    public void putDataSource(String key, DataSource dataSource) {
        dbNameToDataSourceMap.put(key, dataSource);
    }

    public void removeDataSource(String dbName) {
        DataSource dataSource = dbNameToDataSourceMap.remove(dbName);
        if (dataSource instanceof HikariDataSource hds) {
            hds.close();
        }
    }

    public void withCurrentDb(String dbName, Runnable action) {
        setCurrentDb(dbName);
        try {
            action.run();
        } finally {
            resetCurrentDbAndDisposeDataSource();
        }
    }

    @Override
    protected String determineCurrentLookupKey() {
        if (overriddenDbName.get() != null) {
            return overriddenDbName.get();
        }
        if (!databasePool.isInitialized()) {
            return SYSTEM_DB_KEY;
        }
        String databaseName = databasePool.getNextDatabaseName();
        dbNameToDataSourceMap.computeIfAbsent(databaseName, dataSourceFactory::createHikariDataSource);
        return databaseName;
    }

    @Override
    protected DataSource determineTargetDataSource() {
        String dbName = determineCurrentLookupKey();
        DataSource dataSource = dbNameToDataSourceMap.get(dbName);
        if (dataSource == null) {
            log.warn("No DataSource found for current lookup key '{}', falling back to default", dbName);
            return Objects.requireNonNull(getResolvedDefaultDataSource());
        }
        return dataSource;
    }

    private void setCurrentDb(String dbName) {
        if (!dbNameToDataSourceMap.containsKey(dbName)) {
            throw new IllegalArgumentException("No DataSource found for database: " + dbName);
        }
        overriddenDbName.set(dbName);
    }

    /**
     * Resets current DB override and disposes the corresponding datasource entry.
     * Method is idempotent and does nothing when override is already cleared.
     */
    private void resetCurrentDbAndDisposeDataSource() {
        String dbName = overriddenDbName.get();
        if (dbName == null) {
            return;
        }
        try {
            removeDataSource(dbName);
        } finally {
            overriddenDbName.remove();
        }
    }
}

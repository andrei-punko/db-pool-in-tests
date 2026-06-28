package by.andd3dfx.db.datasource;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import lombok.RequiredArgsConstructor;
import org.postgresql.ds.PGSimpleDataSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;

/**
 * Factory for creating test {@link DataSource} instances.
 * <p>
 * Creates pooled {@link HikariDataSource} for per-test databases and
 * non-pooled {@link PGSimpleDataSource} for template database initialization.
 */
@Component
@RequiredArgsConstructor
public class DataSourceFactory {

    @Value("${spring.datasource.hikari.schema}")
    private String hikariSchemaName;

    private final DataSourceProperties dataSourceProperties;
    private final Environment environment;

    /**
     * Creates a pooled datasource for a test database.
     */
    public DataSource createHikariDataSource(String dbName) {
        return new HikariDataSource(buildHikariConfig(dbName));
    }

    /**
     * Creates a non-pooled datasource for template database operations.
     */
    public DataSource createTemplateDataSource(String dbName) {
        PGSimpleDataSource pgDataSource = new PGSimpleDataSource();
        pgDataSource.setUrl(buildJdbcUrl(dbName));
        pgDataSource.setUser(dataSourceProperties.getUsername());
        pgDataSource.setPassword(dataSourceProperties.getPassword());
        pgDataSource.setCurrentSchema(hikariSchemaName);
        return pgDataSource;
    }

    private HikariConfig buildHikariConfig(String dbName) {
        HikariConfig config = new HikariConfig();
        HikariConfig hikariConfig = Binder.get(environment).bindOrCreate("spring.datasource.hikari", HikariConfig.class);
        hikariConfig.copyStateTo(config);

        config.setJdbcUrl(buildJdbcUrl(dbName));
        config.setUsername(dataSourceProperties.getUsername());
        config.setPassword(dataSourceProperties.getPassword());
        config.setSchema(hikariSchemaName);
        config.setPoolName("testdb-hikari-" + dbName);
        return config;
    }

    private String buildJdbcUrl(String dbName) {
        return dataSourceProperties.getUrl().replaceFirst("/test[a-zA-Z0-9_-]*", "/" + dbName);
    }
}

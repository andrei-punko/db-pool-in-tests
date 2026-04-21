package by.andd3dfx;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;
import org.postgresql.ds.PGSimpleDataSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;

/**
 * Фабрика тестовых {@link DataSource}: пул Hikari для клонов и непуловый {@link PGSimpleDataSource} для template.
 */
@Component
@RequiredArgsConstructor
public class DataSourceFactory {

    @Value("${spring.datasource.hikari.schema}")
    private String hikariSchemaName;

    private final DataSourceProperties dataSourceProperties;
    private final Environment environment;

    public DataSource createHikariDataSource(String dbName) {
        return new HikariDataSource(buildHikariConfig(dbName));
    }

    public DataSource createTemplateDataSource(String dbName) {
        PGSimpleDataSource pgDataSource = new PGSimpleDataSource();
        pgDataSource.setUrl(buildJdbcUrl(dbName));
        pgDataSource.setUser(dataSourceProperties.getUsername());
        pgDataSource.setPassword(dataSourceProperties.getPassword());
        pgDataSource.setCurrentSchema(hikariSchemaName);
        return pgDataSource;
    }

    private @NonNull HikariConfig buildHikariConfig(String dbName) {
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

    private @NonNull String buildJdbcUrl(String dbName) {
        return dataSourceProperties.getUrl().replaceFirst("/test[a-zA-Z0-9_-]*", "/" + dbName);
    }
}

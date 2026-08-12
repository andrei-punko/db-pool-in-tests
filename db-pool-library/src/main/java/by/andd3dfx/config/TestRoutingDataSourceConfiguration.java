package by.andd3dfx.config;

import by.andd3dfx.db.DataSourceFactory;
import by.andd3dfx.db.RoutingDataSource;
import by.andd3dfx.db.DatabasePool;
import org.postgresql.ds.PGSimpleDataSource;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import javax.sql.DataSource;

import java.util.Map;

/**
 * Enabled under profile {@code it}: provides a routing DataSource for the test database pool.
 */
@Profile("it")
@Configuration(proxyBeanMethods = false)
public class TestRoutingDataSourceConfiguration {

    @Bean
    @Primary
    DataSource dataSource(DataSourceProperties properties, DataSourceFactory dataSourceFactory, @Lazy DatabasePool databasePool) {
        RoutingDataSource routing = new RoutingDataSource(dataSourceFactory, databasePool);
        DataSource systemDataSource = systemDataSource(properties);
        routing.setDefaultTargetDataSource(systemDataSource);
        routing.putDataSource(RoutingDataSource.SYSTEM_DB_KEY, systemDataSource);
        routing.setTargetDataSources(Map.of(RoutingDataSource.SYSTEM_DB_KEY, systemDataSource));
        routing.afterPropertiesSet();
        return routing;
    }

    @Bean(name = "dwhJdbcTemplate")
    NamedParameterJdbcTemplate dwhJdbcTemplate(DataSource dataSource) {
        return new NamedParameterJdbcTemplate(dataSource);
    }

    private DataSource systemDataSource(DataSourceProperties properties) {
        return properties
                .initializeDataSourceBuilder().type(PGSimpleDataSource.class).build();
    }
}

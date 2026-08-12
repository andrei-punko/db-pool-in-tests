package by.andd3dfx.config;

import by.andd3dfx.db.DataSourceFactory;
import by.andd3dfx.db.Metrics;
import by.andd3dfx.db.DatabasePool;
import by.andd3dfx.db.DatabasePoolLifecycleService;
import by.andd3dfx.db.DatabaseTemplateService;
import by.andd3dfx.sql.SqlSupport;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Profile;

/**
 * Use in integration tests (profile {@code it}): {@code @Import(DbPoolTestSupportConfiguration.class)}.
 * <p>
 * To customize template schema preparation, provide your own {@link TestDatabaseSchemaPreparer} bean.
 */
@Configuration(proxyBeanMethods = false)
@Profile("it")
@Import({
        DataSourceFactory.class,
        SqlSupport.class,
        DatabaseTemplateService.class,
        DatabasePool.class,
        DatabasePoolLifecycleService.class,
        Metrics.class,
        TestRoutingDataSourceConfiguration.class
})
public class DbPoolTestSupportConfiguration {

    @Bean
    @ConditionalOnMissingBean(TestDatabaseSchemaPreparer.class)
    TestDatabaseSchemaPreparer noopTestDatabaseSchemaPreparer() {
        return () -> {
        };
    }
}

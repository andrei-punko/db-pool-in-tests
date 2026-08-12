package by.andd3dfx.example;

import by.andd3dfx.config.TestDatabaseSchemaPreparer;
import by.andd3dfx.sql.SqlSupport;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

@TestConfiguration
public class ExampleDbPoolTestConfiguration {

    @Bean
    TestDatabaseSchemaPreparer testDatabaseSchemaPreparer(SqlSupport sqlSupport) {
        return new ExampleTestDatabaseSchemaPreparer(sqlSupport);
    }
}

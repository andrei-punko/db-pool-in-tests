package by.andd3dfx.example;

import by.andd3dfx.DbPoolTestSupportConfiguration;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;

/**
 * Minimal demo application: imports the DB pool configuration for integration tests.
 */
@SpringBootApplication
@Import(DbPoolTestSupportConfiguration.class)
public class ExampleDbPoolDemoApplication {

    public static void main(String[] args) {
        org.springframework.boot.SpringApplication.run(ExampleDbPoolDemoApplication.class, args);
    }
}

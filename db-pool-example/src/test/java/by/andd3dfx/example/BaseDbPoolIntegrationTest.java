package by.andd3dfx.example;

import by.andd3dfx.db.pool.DatabasePoolLifecycleService;
import by.andd3dfx.db.pool.DatabasePoolTimeLoggingExtension;
import by.andd3dfx.testcontainers.ContainersLifecycleSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@SpringBootTest(classes = ExampleDbPoolDemoApplication.class)
@Import(ExampleDbPoolTestConfiguration.class)
@ActiveProfiles({"it", "testcontainer"})
@ExtendWith(DatabasePoolTimeLoggingExtension.class)
public abstract class BaseDbPoolIntegrationTest {

    @DynamicPropertySource
    static void registerPostgres(DynamicPropertyRegistry registry) {
        ContainersLifecycleSupport.postgresqlProperties(registry);
    }

    @Autowired
    protected JdbcTemplate jdbcTemplate;

    @Autowired
    private DatabasePoolLifecycleService databasePoolLifecycleService;

    @AfterEach
    void releaseDb() {
        databasePoolLifecycleService.releaseCurrentDatabase();
    }
}

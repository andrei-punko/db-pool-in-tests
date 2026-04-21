package by.andd3dfx.example;

import by.andd3dfx.DatabasePoolLifecycleService;
import by.andd3dfx.DatabasePoolTimeLoggingExtension;
import by.andd3dfx.testcontainers.ContainersLifecycleSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Пример: Testcontainers PostgreSQL + динамические свойства + пул клонов из библиотеки.
 */
@SpringBootTest(classes = ExampleDbPoolDemoApplication.class)
@ActiveProfiles({"it", "testcontainer"})
@ExtendWith(DatabasePoolTimeLoggingExtension.class)
class ExampleDbPoolApplicationTest {

    @DynamicPropertySource
    static void registerPostgres(DynamicPropertyRegistry registry) {
        ContainersLifecycleSupport.postgresqlProperties(registry);
    }

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private DatabasePoolLifecycleService databasePoolLifecycleService;

    @AfterEach
    void releaseDb() {
        databasePoolLifecycleService.releaseCurrentDatabase();
    }

    @Test
    void jdbcSeesDatabaseFromPool() {
        Integer one = jdbcTemplate.queryForObject("SELECT 1", Integer.class);
        assert one != null && one == 1;
    }
}

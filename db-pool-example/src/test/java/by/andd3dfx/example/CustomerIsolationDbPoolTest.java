package by.andd3dfx.example;

import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import static org.assertj.core.api.Assertions.assertThat;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class CustomerIsolationDbPoolTest extends BaseDbPoolIntegrationTest {

    @Test
    @Order(1)
    void firstTestInsertsCustomerIntoItsClone() {
        jdbcTemplate.update(
                "INSERT INTO customer (email, name) VALUES ('isolation-test@example.com', 'Isolation')"
        );

        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM customer", Integer.class)).isEqualTo(3);
    }

    @Test
    @Order(2)
    void secondTestGetsFreshCloneWithoutFirstTestData() {
        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM customer", Integer.class);
        Integer isolationRows = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM customer WHERE email = 'isolation-test@example.com'",
                Integer.class
        );

        assertThat(count).isEqualTo(2);
        assertThat(isolationRows).isZero();
    }
}

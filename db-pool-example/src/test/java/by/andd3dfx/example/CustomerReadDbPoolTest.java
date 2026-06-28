package by.andd3dfx.example;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CustomerReadDbPoolTest extends BaseDbPoolIntegrationTest {

    @Test
    void readsSeedCustomersFromPooledClone() {
        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM customer", Integer.class);
        String aliceName = jdbcTemplate.queryForObject(
                "SELECT name FROM customer WHERE email = 'alice@example.com'",
                String.class
        );

        assertThat(count).isEqualTo(2);
        assertThat(aliceName).isEqualTo("Alice");
    }
}

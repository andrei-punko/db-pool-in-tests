package by.andd3dfx.example;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CustomerWriteDbPoolTest extends BaseDbPoolIntegrationTest {

    @Test
    void insertsCustomerIntoIsolatedClone() {
        jdbcTemplate.update(
                "INSERT INTO customer (email, name) VALUES ('carol@example.com', 'Carol')"
        );

        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM customer", Integer.class);
        String carolName = jdbcTemplate.queryForObject(
                "SELECT name FROM customer WHERE email = 'carol@example.com'",
                String.class
        );

        assertThat(count).isEqualTo(3);
        assertThat(carolName).isEqualTo("Carol");
    }
}

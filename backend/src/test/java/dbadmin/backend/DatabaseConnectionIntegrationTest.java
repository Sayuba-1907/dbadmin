package dbadmin.backend;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

// En basit seviyede: backend'in gercek Postgres'e (Testcontainers) baglanip
// baglanamadigini dogrular. Connection acilabiliyor mu, basit bir sorgu donuyor mu.
class DatabaseConnectionIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void connectionCanBeOpened() throws Exception {
        try (Connection connection = jdbcTemplate.getDataSource().getConnection()) {
            assertTrue(connection.isValid(2));
            connection.prepareCall("select 1").execute();
            System.out.println("Connection aldı");
            // while(true){
            //
            // }
        }
    }

    @Test
    void simpleQueryReturnsExpectedResult() {
        Integer result = jdbcTemplate.queryForObject("SELECT 1", Integer.class);
        assertEquals(1, result);
    }
}

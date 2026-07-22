package dbadmin.backend;

import org.junit.jupiter.api.Test;

// Was a bare @SpringBootTest with no datasource - it only ever passed
// inside docker compose, where SPRING_DATASOURCE_URL is injected by the
// environment. Extending AbstractIntegrationTest gives it the same
// Testcontainers Postgres the other integration tests use, so it actually
// verifies the full application context boots on its own.
class BackendApplicationTests extends AbstractIntegrationTest {

    @Test
    void contextLoads() {
    }

}

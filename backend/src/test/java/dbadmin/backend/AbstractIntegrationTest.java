package dbadmin.backend;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

// Shared real Postgres for every integration test - not H2, not a mock,
// per the assignment's testing requirement. Uses Testcontainers' documented
// "singleton container" pattern: a static initializer block, not @Testcontainers
// + @Container. The block runs exactly once when this class is first loaded,
// so the container is genuinely shared across every subclass in the JVM run.
// (The @Testcontainers/@Container annotation combo looked equivalent but, in
// practice on this project, caused a fresh container per subclass instead of
// one shared instance - the static block sidesteps that entirely.)
@SpringBootTest
@org.springframework.context.annotation.Import(MockMvcSecurityTestConfig.class)
public abstract class AbstractIntegrationTest {

    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:15-alpine");

    static {
        POSTGRES.start();
    }

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }
}

package dbadmin.backend;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
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

    // docker-compose.yml'de redis'e host'tan erisilebilir bir port acilmiyor (expose, ports
    // degil - guvenlik gerekcesiyle bilincli, bkz. DECISIONS.md). Bu yuzden host'tan
    // `./mvnw test` calistirildiginda actuator health Redis'i DOWN gorup 503 donuyordu
    // (SecurityRulesIntegrationTest#actuatorHealth_kimliksiz_erisilebilir_kalmali). Testler
    // kendi izole Redis'ini Testcontainers ile ayaga kaldirir - gercek uygulama compose
    // kurulumuna dokunulmuyor, sadece test ortami artik gercekten erisebildigi bir Redis
    // gorüyor.
    static final GenericContainer<?> REDIS =
            new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);

    static {
        POSTGRES.start();
        REDIS.start();
    }

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
    }
}

package dbadmin.backend;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.containers.RabbitMQContainer;

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

    // Audit log yedekleme (bkz. requirement-maintenance-audit-backup.md) icin: mock'lanmis bir
    // MinioClient yerine Redis'teki gibi gercek bir container. MinioBucketInitializer acilista
    // gercekten bucketExists() cagirdigi icin (bkz. config/MinioBucketInitializer), endpoint'in
    // erisilebilir olmasi bean olusturma asamasinda zaten sart.
    static final GenericContainer<?> MINIO =
            new GenericContainer<>("minio/minio:latest")
                    .withCommand("server", "/data")
                    .withEnv("MINIO_ROOT_USER", "testminioadmin")
                    .withEnv("MINIO_ROOT_PASSWORD", "testminiosecret")
                    .withExposedPorts(9000)
                    .waitingFor(Wait.forHttp("/minio/health/live").forPort(9000));

    // Bildirim push'u artik RabbitMQ uzerinden gectigi icin (bkz. NotificationService/
    // RabbitNotificationListener) push'u dogrulayan testler gercek bir broker'a ihtiyac duyar —
    // Redis/MinIO'yla ayni gerekce.
    static final RabbitMQContainer RABBITMQ =
            new RabbitMQContainer("rabbitmq:3-management-alpine");

    static {
        POSTGRES.start();
        REDIS.start();
        MINIO.start();
        RABBITMQ.start();
    }

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
        registry.add("app.minio.endpoint",
                () -> "http://" + MINIO.getHost() + ":" + MINIO.getMappedPort(9000));
        registry.add("app.minio.access-key", () -> "testminioadmin");
        registry.add("app.minio.secret-key", () -> "testminiosecret");
        registry.add("spring.rabbitmq.host", RABBITMQ::getHost);
        registry.add("spring.rabbitmq.port", RABBITMQ::getAmqpPort);
        registry.add("spring.rabbitmq.username", RABBITMQ::getAdminUsername);
        registry.add("spring.rabbitmq.password", RABBITMQ::getAdminPassword);
    }
}

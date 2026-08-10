package dbadmin.backend.config;

import io.minio.MinioClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Audit log yedeklerinin (bkz. requirement-maintenance-audit-backup.md) yazildigi MinIO
 * baglantisi. Bucket'in ilk acilista var oldugunu garanti eden kisim icin bkz.
 * {@link MinioBucketInitializer} — Redis/Postgres'ten farkli olarak bucket disaridan hazir
 * gelmez, ilk yedekleme denemesi bucket yoksa {@code NoSuchBucketException} ile patlar.
 */
@Configuration
public class MinioConfig {

    @Bean
    public MinioClient minioClient(
            @Value("${app.minio.endpoint}") String endpoint,
            @Value("${app.minio.access-key}") String accessKey,
            @Value("${app.minio.secret-key}") String secretKey) {
        return MinioClient.builder()
                .endpoint(endpoint)
                .credentials(accessKey, secretKey)
                .build();
    }
}

package dbadmin.backend.config;

import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * Uygulama acilirken {@code app.minio.bucket} yoksa olusturur. {@link UserSeeder}'daki
 * "kosul kendi kendine korunur, migration script'i degil" mantiginin ayni sekilde MinIO'ya
 * uygulanmasi — bucket container'in kendi ilk-acilis state'i, Postgres'teki gibi disaridan
 * hazir gelmiyor.
 */
@Component
public class MinioBucketInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(MinioBucketInitializer.class);

    private final MinioClient minioClient;
    private final String bucket;

    public MinioBucketInitializer(MinioClient minioClient, @Value("${app.minio.bucket}") String bucket) {
        this.minioClient = minioClient;
        this.bucket = bucket;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        boolean exists = minioClient.bucketExists(BucketExistsArgs.builder().bucket(bucket).build());
        if (exists) {
            return;
        }
        minioClient.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
        log.info("MinIO bucket '{}' yoktu, olusturuldu.", bucket);
    }
}

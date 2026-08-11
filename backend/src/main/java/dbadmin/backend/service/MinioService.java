package dbadmin.backend.service;

import dbadmin.backend.exception.BackupFailedException;
import io.minio.GetObjectArgs;
import io.minio.ListObjectsArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import io.minio.Result;
import io.minio.messages.Item;
import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * MinIO ile etkilesimin tek noktasi (bkz. requirement-maintenance-audit-backup.md). Uygulamanin
 * MinIO'daki tek klasoru ({@code app.minio.bucket}) icin yazma (yedekleme), listeleme ve okuma
 * (yedekler listesi ekrani) burada toplanir — bucket yonetimi ayri, bkz. {@code
 * config/MinioBucketInitializer}.
 */
@Service
public class MinioService {

    private final MinioClient minioClient;
    private final String bucket;

    public MinioService(MinioClient minioClient, @Value("${app.minio.bucket}") String bucket) {
        this.minioClient = minioClient;
        this.bucket = bucket;
    }

    /**
     * MinIO SDK'sinin firlattigi checked exception'lar (ErrorResponseException, IOException,
     * ServerException, vb.) burada {@link BackupFailedException}'a sarilir — cagiran
     * ({@code AuditLogBackupService}) bunu fail-closed olarak yukari birakir (Req-3.3), DB
     * silme adimina hic gecmez.
     */
    public void upload(String key, byte[] content, String contentType) {
        try {
            minioClient.putObject(PutObjectArgs.builder()
                    .bucket(bucket)
                    .object(key)
                    .stream(new ByteArrayInputStream(content), (long) content.length, -1L)
                    .contentType(contentType)
                    .build());
        } catch (Exception ex) {
            throw new BackupFailedException("MinIO upload failed for key '" + key + "'", ex);
        }
    }

    /**
     * SADECE {@code backup-} on-ekiyle baslayan dosyalarin adlarini doner —
     * {@code AuditLogBackupService#listBackups} bunlari tek tek indirip JSON olarak parse eder.
     * Bucket, uygulamanin TEK MinIO klasoru (audit log yedekleri VE CSV export'lari {@code
     * TableDataService#exportCsv} ile ayni bucket'ta, {@code csv-exports/} altinda birlikte
     * yasiyor) — prefix filtresi olmadan CSV dosyalari da buraya duser ve JSON parse'i patlatirdi
     * (bu bug bir kere gercekten yasandi, bkz. DECISIONS.md).
     */
    public List<String> listBackupKeys() {
        try {
            List<String> keys = new ArrayList<>();
            for (Result<Item> result : minioClient.listObjects(
                    ListObjectsArgs.builder().bucket(bucket).prefix("backup-").build())) {
                keys.add(result.get().objectName());
            }
            return keys;
        } catch (Exception ex) {
            throw new BackupFailedException("MinIO'daki yedek dosyalari listelenemedi", ex);
        }
    }

    /** Tek bir yedek dosyasinin icerigini indirir. */
    public byte[] download(String key) {
        try (var stream = minioClient.getObject(GetObjectArgs.builder().bucket(bucket).object(key).build())) {
            return stream.readAllBytes();
        } catch (Exception ex) {
            throw new BackupFailedException("MinIO'dan '" + key + "' indirilemedi", ex);
        }
    }

    /** Bir nesneyi siler — profil fotografi kaldirilirken kullanilir (bkz. UserService#removeAvatar). */
    public void delete(String key) {
        try {
            minioClient.removeObject(RemoveObjectArgs.builder().bucket(bucket).object(key).build());
        } catch (Exception ex) {
            throw new BackupFailedException("MinIO'dan '" + key + "' silinemedi", ex);
        }
    }
}

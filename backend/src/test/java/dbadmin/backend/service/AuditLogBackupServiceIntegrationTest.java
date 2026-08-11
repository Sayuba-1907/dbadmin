package dbadmin.backend.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dbadmin.backend.AbstractIntegrationTest;
import dbadmin.backend.dto.AuditLogBackupListItemDto;
import dbadmin.backend.dto.AuditLogBackupResponse;
import dbadmin.backend.entity.AuditLog;
import dbadmin.backend.exception.ValidationException;
import dbadmin.backend.repository.AuditLogRepository;
import io.minio.GetObjectArgs;
import io.minio.MinioClient;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.test.context.support.WithMockUser;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * requirement-maintenance-audit-backup.md Faz 6: gercek Postgres + gercek MinIO'ya karsi
 * (AbstractIntegrationTest'teki MinIO Testcontainer'i, bkz. o sinifin javadoc'u) — mock yok,
 * projenin "gercek DB, mock yok" ilkesinin MinIO'ya da uygulanmis hali.
 * <p>
 * Upload basarisiz oldugunda DB'ye dokunulmadigini (fail-closed) dogrulayan test BILEREK ayri
 * bir sinifta ({@link AuditLogBackupServiceFailureIntegrationTest}) — orada {@code MinioService}
 * mock'lanir, burada mock'lanmasi geri kalan testlerin gercek-MinIO dogrulamasini bozardi.
 */
@WithMockUser(username = "admin", roles = "ADMIN")
class AuditLogBackupServiceIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private AuditLogBackupService auditLogBackupService;

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Autowired
    private SchemaService schemaService;

    @Autowired
    private MinioClient minioClient;

    @Autowired
    private ObjectMapper objectMapper;

    @Value("${app.minio.bucket}")
    private String bucket;

    /** Diger test siniflarinin (paylasilan Postgres container) biraktigi satirlari temizleyip kendi izole senaryosunu kurar. */
    private void resetAuditLog() {
        auditLogRepository.deleteAll();
    }

    @Test
    void backup_tumSatirlariYedeklerVeTabloyuTemizler() throws Exception {
        resetAuditLog();
        schemaService.createSchema("backup_test_sema_1");
        schemaService.createSchema("backup_test_sema_2");
        long rowCountBefore = auditLogRepository.count();
        assertEquals(2, rowCountBefore);

        AuditLogBackupResponse result = auditLogBackupService.backup();

        assertEquals(2, result.rowCount());
        assertEquals(0, auditLogRepository.count(), "Req-2.5: yedeklenen satirlar DB'den silinmeli");

        JsonNode file = readBackupFile(result.key());
        assertEquals("admin", file.get("meta").get("backedUpBy").asText());
        assertEquals(2, file.get("meta").get("rowCount").asInt());
        assertEquals(2, file.get("entries").size());
        assertFalse(file.get("entries").get(0).has("id"),
                "Req-2.4.2: id alani yedege dahil edilmemeli");
        assertTrue(file.get("entries").get(0).has("username"));
    }

    @Test
    void backup_bosTabloda_validationExceptionFirlatir() {
        resetAuditLog();

        assertThrows(ValidationException.class, () -> auditLogBackupService.backup());
        assertEquals(0, auditLogRepository.count());
    }

    /**
     * {@link AuditLogRepository#deleteByIdLessThanEqual}'in asil garantisi (Req-2.5): cutoff'un
     * ustundeki (backup okumasindan SONRA yazilan) bir satir yanlislikla silinmemeli. Gercek
     * race condition'i (okuma ile upload arasindaki network gecikmesi) tetiklemek yerine,
     * mekanizmayi dogrudan sinar — ayni garanti, daha az kirilgan bir test.
     */
    @Test
    void deleteByIdLessThanEqual_cutoffUstundekiSatirKorunur() {
        resetAuditLog();
        schemaService.createSchema("cutoff_test_a");
        long cutoffId = auditLogRepository.findAllByOrderByIdAsc().get(0).getId();
        schemaService.createSchema("cutoff_test_b");

        auditLogRepository.deleteByIdLessThanEqual(cutoffId);

        List<AuditLog> remaining = auditLogRepository.findAllByOrderByIdAsc();
        assertEquals(1, remaining.size(), "cutoff'tan sonra yazilan satir silinmemeli");
        assertTrue(remaining.get(0).getId() > cutoffId);
    }

    /**
     * Req-2.6 (frontend'in "Geçmiş Yedekler" listesi): MinIO'daki paylasilan bucket'ta
     * baska testlerden kalma dosyalar da olabilecegi icin (izole edilmiyor, sadece MinIO'ya
     * yaziliyor, silinmiyor), toplam sayi yerine YENI olusturulan yedegin listede dogru meta
     * bilgiyle gorundugu dogrulaniyor.
     */
    @Test
    void listBackups_yeniYedekDogruMetaIleListelenir() {
        resetAuditLog();
        schemaService.createSchema("list_test_sema");

        AuditLogBackupResponse result = auditLogBackupService.backup();
        List<AuditLogBackupListItemDto> backups = auditLogBackupService.listBackups();

        AuditLogBackupListItemDto found = backups.stream()
                .filter(b -> b.key().equals(result.key()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("yeni olusturulan yedek listede bulunamadi"));
        assertEquals("admin", found.backedUpBy());
        assertEquals(1, found.rowCount());
    }

    /** Yeni indirme ucunun (Req-2.6 genisletmesi) gercek yuklenmis dosyayi eksiksiz dondurdugunu dogrular. */
    @Test
    void downloadBackup_gecerliKey_dosyaIcerigineDoner() throws Exception {
        resetAuditLog();
        schemaService.createSchema("download_test_sema");
        AuditLogBackupResponse result = auditLogBackupService.backup();

        byte[] content = auditLogBackupService.downloadBackup(result.key());

        JsonNode file = objectMapper.readTree(content);
        assertEquals("admin", file.get("meta").get("backedUpBy").asText());
        assertEquals(1, file.get("meta").get("rowCount").asInt());
    }

    /** Path traversal / rastgele MinIO key'i denemesi (bkz. AuditLogBackupService#KEY_PATTERN) — format tutmuyorsa MinIO'ya hic gidilmemeli. */
    @Test
    void downloadBackup_gecersizKeyFormati_validationExceptionFirlatir() {
        assertThrows(ValidationException.class,
                () -> auditLogBackupService.downloadBackup("../../etc/passwd"));
    }

    private JsonNode readBackupFile(String key) throws Exception {
        try (var stream = minioClient.getObject(
                GetObjectArgs.builder().bucket(bucket).object(key).build())) {
            return objectMapper.readTree(stream.readAllBytes());
        }
    }
}

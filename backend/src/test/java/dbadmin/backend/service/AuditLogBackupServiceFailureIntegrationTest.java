package dbadmin.backend.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;

import dbadmin.backend.AbstractIntegrationTest;
import dbadmin.backend.exception.BackupFailedException;
import dbadmin.backend.repository.AuditLogRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * requirement-maintenance-audit-backup.md Req-3.3 (fail-closed): MinIO yuklemesi basarisiz
 * olursa {@code audit_log} tablosuna HIC dokunulmamali. {@link MinioService} bilerek
 * mock'lanmis, gercek bir yerel/agsal hatayi (MinIO container'i durdurmak, vs.) simule etmenin
 * kararli bir yolu yok — bu ayri sinifta yasiyor ki {@code @MockitoBean} sadece bu sinifin
 * context'ini etkilesin, {@link AuditLogBackupServiceIntegrationTest}'teki gercek-MinIO
 * dogrulamalarini bozmasin.
 */
@WithMockUser(username = "admin", roles = "ADMIN")
class AuditLogBackupServiceFailureIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private AuditLogBackupService auditLogBackupService;

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Autowired
    private SchemaService schemaService;

    @MockitoBean
    private MinioService minioService;

    @Test
    void backup_uploadBasarisizOlursa_dbSatirlariSilinmez() {
        auditLogRepository.deleteAll();
        schemaService.createSchema("backup_fail_test_sema");
        long rowCountBefore = auditLogRepository.count();
        assertEquals(1, rowCountBefore);
        doThrow(new BackupFailedException("test: MinIO erisilemedi", new RuntimeException("baglanti reddedildi")))
                .when(minioService)
                .upload(anyString(), any(byte[].class), anyString());

        assertThrows(BackupFailedException.class, () -> auditLogBackupService.backup());

        assertEquals(rowCountBefore, auditLogRepository.count(),
                "Req-3.3: upload basarisiz olursa hicbir audit satiri silinmemis olmali");
    }
}

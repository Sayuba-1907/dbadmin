package dbadmin.backend.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dbadmin.backend.AbstractIntegrationTest;
import dbadmin.backend.entity.AuditLog;
import dbadmin.backend.entity.TargetType;
import dbadmin.backend.entity.OperationType;
import dbadmin.backend.entity.Schema;
import dbadmin.backend.entity.DataTable;
import dbadmin.backend.repository.AuditLogRepository;
import dbadmin.backend.repository.SchemaRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithMockUser;

/**
 * requirement-audit-log.md Faz 4: bir mutasyonun gercekten kalici bir audit satiri urettigini
 * (Adim 4.1) ve DDL basarisiz oldugunda audit satirinin da yazilmadigini — yani tum islemin
 * (metadata + DDL + audit) tek transaction olarak geri alindigini (Adim 4.2, Req-3.1 fail-closed)
 * dogrular.
 */
@WithMockUser(username = "admin", roles = "ADMIN")
class AuditLogServiceIntegrationTest extends AbstractIntegrationTest {

    private static final String TEST_SCHEMA = "audit_test_sema";

    @Autowired
    private TableService tableService;

    @Autowired
    private SchemaService schemaService;

    @Autowired
    private AuditLogService auditLogService;

    @Autowired
    private SchemaRepository schemaRepository;

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Long testSchemaId;

    @BeforeEach
    void ensureTestSchema() {
        testSchemaId = schemaRepository.findByNameIgnoreCase(TEST_SCHEMA)
                .map(Schema::getId)
                .orElseGet(() -> schemaService.createSchema(TEST_SCHEMA).getId());
    }

    @Test
    void createTablo_kaliciBirAuditSatiriUretir() {
        DataTable table = tableService.createTable("audit_urun1", testSchemaId,
                List.of(new ColumnSpec("ad", "text", null)));

        List<AuditLog> hepsi = auditLogRepository.findAll();
        Optional<AuditLog> ilgiliSatir = hepsi.stream()
                .filter(a -> a.getOperationType() == OperationType.TABLE_CREATED
                        && a.getTargetId().equals(table.getId()))
                .findFirst();

        assertTrue(ilgiliSatir.isPresent(), "TABLE_CREATED audit satiri bulunamadi");
        AuditLog satir = ilgiliSatir.get();
        assertEquals(TargetType.TABLE, satir.getTargetType());
        assertEquals("admin", satir.getUsername());
    }

    /** Req-2.4: {@code targetId} filtresi, ayni turden farkli hedeflere ait satirlar arasindan sadece istenen hedefi doner. */
    @Test
    void search_targetIdFiltresi_sadeceOHedefinSatirlariniDoner() {
        DataTable table1 = tableService.createTable("audit_hedef1", testSchemaId,
                List.of(new ColumnSpec("ad", "text", null)));
        DataTable table2 = tableService.createTable("audit_hedef2", testSchemaId,
                List.of(new ColumnSpec("ad", "text", null)));

        Page<AuditLog> sonuc = auditLogService.search(
                null, TargetType.TABLE, table1.getId(), null, null, PageRequest.of(0, 50));

        assertTrue(sonuc.getContent().stream().allMatch(a -> a.getTargetId().equals(table1.getId())),
                "targetId filtresi verilince baska hedefin satiri donmemeli");
        assertTrue(sonuc.getContent().stream()
                        .anyMatch(a -> a.getOperationType() == OperationType.TABLE_CREATED
                                && a.getTargetId().equals(table1.getId())),
                "table1'in kendi TABLE_CREATED satiri sonucta olmali");
        assertTrue(sonuc.getContent().stream().noneMatch(a -> a.getTargetId().equals(table2.getId())),
                "table2'nin satirlari sonuca sizmamali");
    }

    /**
     * DDL, PRIMARY KEY kolonlarinin otomatik NOT NULL yapilmasi yuzunden mevcut NULL degerlere
     * takilip reddedilir (bkz. TableService.changeColumnPrimaryKey javadoc'u) — bu, metadata'nin
     * gecici olarak degistirilip DDL asamasinda patladigi, gercek bir "yarim kalmis islem"
     * senaryosu. Beklenen: transaction tamamen geri alinir, audit satiri da yazilmaz.
     */
    @Test
    void changeKolonPrimaryKey_ddlBasarisizOlursa_auditSatiriYazilmaz() {
        DataTable table = tableService.createTable("audit_urun2", testSchemaId,
                List.of(new ColumnSpec("kolona", "text", null, true),
                        new ColumnSpec("kolonb", "text", null, false)));
        jdbcTemplate.update("INSERT INTO \"" + TEST_SCHEMA + "\".\"audit_urun2\" (kolona) VALUES ('x')");
        Long kolonbId = table.getColumns().get(1).getId();
        long auditSayisiOncesi = auditLogRepository.count();

        assertThrows(DataIntegrityViolationException.class,
                () -> tableService.changeColumnPrimaryKey(table.getId(), kolonbId, true));

        assertEquals(auditSayisiOncesi, auditLogRepository.count(),
                "DDL basarisiz oldugunda audit satiri da yazilmamis olmali (fail-closed, Req-3.1)");
    }
}

package dbadmin.backend.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dbadmin.backend.AbstractIntegrationTest;
import dbadmin.backend.entity.AuditLog;
import dbadmin.backend.entity.HedefTip;
import dbadmin.backend.entity.IslemTipi;
import dbadmin.backend.entity.Schema;
import dbadmin.backend.entity.Tablo;
import dbadmin.backend.repository.AuditLogRepository;
import dbadmin.backend.repository.SchemaRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
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
    private TabloService tabloService;

    @Autowired
    private SchemaService schemaService;

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
        Tablo tablo = tabloService.createTablo("audit_urun1", testSchemaId,
                List.of(new KolonTanimi("ad", "text", null)));

        List<AuditLog> hepsi = auditLogRepository.findAll();
        Optional<AuditLog> ilgiliSatir = hepsi.stream()
                .filter(a -> a.getIslemTipi() == IslemTipi.TABLO_OLUSTURULDU
                        && a.getHedefId().equals(tablo.getId()))
                .findFirst();

        assertTrue(ilgiliSatir.isPresent(), "TABLO_OLUSTURULDU audit satiri bulunamadi");
        AuditLog satir = ilgiliSatir.get();
        assertEquals(HedefTip.TABLO, satir.getHedefTip());
        assertEquals("admin", satir.getKullaniciAdi());
    }

    /**
     * DDL, PRIMARY KEY kolonlarinin otomatik NOT NULL yapilmasi yuzunden mevcut NULL degerlere
     * takilip reddedilir (bkz. TabloService.changeKolonPrimaryKey javadoc'u) — bu, metadata'nin
     * gecici olarak degistirilip DDL asamasinda patladigi, gercek bir "yarim kalmis islem"
     * senaryosu. Beklenen: transaction tamamen geri alinir, audit satiri da yazilmaz.
     */
    @Test
    void changeKolonPrimaryKey_ddlBasarisizOlursa_auditSatiriYazilmaz() {
        Tablo tablo = tabloService.createTablo("audit_urun2", testSchemaId,
                List.of(new KolonTanimi("kolona", "text", null, true),
                        new KolonTanimi("kolonb", "text", null, false)));
        jdbcTemplate.update("INSERT INTO \"" + TEST_SCHEMA + "\".\"audit_urun2\" (kolona) VALUES ('x')");
        Long kolonbId = tablo.getKolonlar().get(1).getId();
        long auditSayisiOncesi = auditLogRepository.count();

        assertThrows(DataIntegrityViolationException.class,
                () -> tabloService.changeKolonPrimaryKey(tablo.getId(), kolonbId, true));

        assertEquals(auditSayisiOncesi, auditLogRepository.count(),
                "DDL basarisiz oldugunda audit satiri da yazilmamis olmali (fail-closed, Req-3.1)");
    }
}

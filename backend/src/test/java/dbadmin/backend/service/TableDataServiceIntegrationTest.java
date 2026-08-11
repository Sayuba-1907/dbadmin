package dbadmin.backend.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dbadmin.backend.AbstractIntegrationTest;
import dbadmin.backend.dto.TableDataResponse;
import dbadmin.backend.entity.DataTable;
import dbadmin.backend.exception.ValidationException;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithMockUser;

/**
 * requirement notu 7 ("DBeaver'daki gibi Show Data") — {@link TableDataService} gercek Postgres
 * tablosundan okuyor mu, sayfalama dogru mu, size whitelist'i calisiyor mu, hepsi burada
 * dogrulanir. Satirlar bilerek {@code TableService} degil dogrudan {@code JdbcTemplate} ile
 * eklenir — bu, uygulamanin "veri girisi" mekanizmasi degil, sadece test kurulumu.
 */
@WithMockUser(username = "admin", roles = "ADMIN")
class TableDataServiceIntegrationTest extends AbstractIntegrationTest {

    private static final String TEST_SCHEMA = "data_test_sema";

    @Autowired
    private TableDataService tableDataService;

    @Autowired
    private TableService tableService;

    @Autowired
    private SchemaService schemaService;

    @Autowired
    private dbadmin.backend.repository.SchemaRepository schemaRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Long testSchemaId() {
        return schemaRepository.findByNameIgnoreCase(TEST_SCHEMA)
                .orElseGet(() -> schemaService.createSchema(TEST_SCHEMA))
                .getId();
    }

    @Test
    void getData_gercekSatirlariSayfalanmisDoner() {
        DataTable table = tableService.createTable("veri_test_1", testSchemaId(),
                List.of(new ColumnSpec("ad", "text", null)));
        jdbcTemplate.update("INSERT INTO " + TEST_SCHEMA + ".veri_test_1 (ad) VALUES ('a'), ('b'), ('c')");

        TableDataResponse page1 = tableDataService.getData(table.getId(), 0, 20);

        assertEquals(List.of("ad"), page1.columns());
        assertEquals(3, page1.rows().size());
        assertEquals(3, page1.totalRows());
        assertEquals("a", page1.rows().get(0).get("ad"));
    }

    @Test
    void getData_sayfalamaDogruCalisir() {
        DataTable table = tableService.createTable("veri_test_2", testSchemaId(),
                List.of(new ColumnSpec("sira", "numeric", null)));
        for (int i = 1; i <= 25; i++) {
            jdbcTemplate.update("INSERT INTO " + TEST_SCHEMA + ".veri_test_2 (sira) VALUES (?)", i);
        }

        TableDataResponse page1 = tableDataService.getData(table.getId(), 0, 20);
        TableDataResponse page2 = tableDataService.getData(table.getId(), 1, 20);

        assertEquals(20, page1.rows().size());
        assertEquals(5, page2.rows().size());
        assertEquals(25, page1.totalRows());
        assertTrue(!page1.rows().get(0).get("sira").equals(page2.rows().get(0).get("sira")));
    }

    @Test
    void getData_bosTablo_bosListeDonerHataFirlatmaz() {
        DataTable table = tableService.createTable("veri_test_bos", testSchemaId(),
                List.of(new ColumnSpec("ad", "text", null)));

        TableDataResponse page = tableDataService.getData(table.getId(), 0, 20);

        assertEquals(0, page.rows().size());
        assertEquals(0, page.totalRows());
        assertEquals(List.of("ad"), page.columns());
    }

    @Test
    void getData_gecersizSayfaBoyutu_validationExceptionFirlatir() {
        DataTable table = tableService.createTable("veri_test_3", testSchemaId(),
                List.of(new ColumnSpec("ad", "text", null)));

        assertThrows(ValidationException.class, () -> tableDataService.getData(table.getId(), 0, 17));
    }

    @Test
    void getData_negatifSayfa_validationExceptionFirlatir() {
        DataTable table = tableService.createTable("veri_test_4", testSchemaId(),
                List.of(new ColumnSpec("ad", "text", null)));

        assertThrows(ValidationException.class, () -> tableDataService.getData(table.getId(), -1, 20));
    }

    @Test
    void insertRow_gecerliDeger_satirEklenirVeGeriOkunur() {
        DataTable table = tableService.createTable("veri_test_5", testSchemaId(),
                List.of(new ColumnSpec("ad", "text", null), new ColumnSpec("yas", "numeric", null)));

        tableDataService.insertRow(table.getId(), Map.of("ad", "Ayşe", "yas", 30));

        TableDataResponse page = tableDataService.getData(table.getId(), 0, 20);
        assertEquals(1, page.rows().size());
        assertEquals("Ayşe", page.rows().get(0).get("ad"));
    }

    /** Verilmeyen kolon DB'nin varsayilanina (burada NULL) duser — kismi satir eklenebilmeli. */
    @Test
    void insertRow_bazikolonlarVerilmezse_kalanNullOlur() {
        DataTable table = tableService.createTable("veri_test_6", testSchemaId(),
                List.of(new ColumnSpec("ad", "text", null), new ColumnSpec("yas", "numeric", null)));

        tableDataService.insertRow(table.getId(), Map.of("ad", "Kerem"));

        TableDataResponse page = tableDataService.getData(table.getId(), 0, 20);
        assertEquals("Kerem", page.rows().get(0).get("ad"));
        assertNull(page.rows().get(0).get("yas"));
    }

    @Test
    void insertRow_bilinmeyenKolon_validationExceptionFirlatir() {
        DataTable table = tableService.createTable("veri_test_7", testSchemaId(),
                List.of(new ColumnSpec("ad", "text", null)));

        assertThrows(ValidationException.class,
                () -> tableDataService.insertRow(table.getId(), Map.of("olmayan_kolon", "x")));
    }

    @Test
    void insertRow_bosDeger_validationExceptionFirlatir() {
        DataTable table = tableService.createTable("veri_test_8", testSchemaId(),
                List.of(new ColumnSpec("ad", "text", null)));

        assertThrows(ValidationException.class, () -> tableDataService.insertRow(table.getId(), Map.of()));
    }

    /** PK'siz kolona tekrar ayni deger eklenmeye calisilirsa Postgres'in kendi UNIQUE ihlali yukari cikmali. */
    @Test
    void insertRow_primaryKeyIhlali_dataIntegrityViolationFirlatir() {
        DataTable table = tableService.createTable("veri_test_9", testSchemaId(),
                List.of(new ColumnSpec("kod", "numeric", null, true)));

        tableDataService.insertRow(table.getId(), Map.of("kod", 1));

        assertThrows(DataIntegrityViolationException.class,
                () -> tableDataService.insertRow(table.getId(), Map.of("kod", 1)));
    }

    /** PRIMARY KEY varsa sayfalama ona gore sirali olmali — ayni sayfayi iki kez istemek ayni satirlari getirmeli. */
    @Test
    void getData_primaryKeyVarsaKararliSiralamaDoner() {
        DataTable table = tableService.createTable("veri_test_10", testSchemaId(),
                List.of(new ColumnSpec("kod", "numeric", null, true)));
        for (int i = 5; i >= 1; i--) {
            tableDataService.insertRow(table.getId(), Map.of("kod", i));
        }

        TableDataResponse page = tableDataService.getData(table.getId(), 0, 20);

        assertEquals(java.math.BigDecimal.valueOf(1), page.rows().get(0).get("kod"));
        assertEquals(java.math.BigDecimal.valueOf(5), page.rows().get(4).get("kod"));
    }

    @Test
    void updateRow_gecerliDeger_satirGuncellenir() {
        DataTable table = tableService.createTable("veri_test_14", testSchemaId(),
                List.of(new ColumnSpec("kod", "numeric", null, true), new ColumnSpec("ad", "text", null)));
        tableDataService.insertRow(table.getId(), Map.of("kod", 1, "ad", "eski"));

        tableDataService.updateRow(table.getId(), Map.of("kod", 1), Map.of("ad", "yeni"));

        TableDataResponse page = tableDataService.getData(table.getId(), 0, 20);
        assertEquals("yeni", page.rows().get(0).get("ad"));
    }

    @Test
    void updateRow_primaryKeriYokTablo_validationExceptionFirlatir() {
        DataTable table = tableService.createTable("veri_test_15", testSchemaId(),
                List.of(new ColumnSpec("ad", "text", null)));
        tableDataService.insertRow(table.getId(), Map.of("ad", "x"));

        assertThrows(ValidationException.class,
                () -> tableDataService.updateRow(table.getId(), Map.of(), Map.of("ad", "y")));
    }

    @Test
    void updateRow_pkDegistirilmeyeCalisilirsa_validationExceptionFirlatir() {
        DataTable table = tableService.createTable("veri_test_16", testSchemaId(),
                List.of(new ColumnSpec("kod", "numeric", null, true)));
        tableDataService.insertRow(table.getId(), Map.of("kod", 1));

        assertThrows(ValidationException.class,
                () -> tableDataService.updateRow(table.getId(), Map.of("kod", 1), Map.of("kod", 2)));
    }

    @Test
    void updateRow_pkEslesenSatirYok_notFoundExceptionFirlatir() {
        DataTable table = tableService.createTable("veri_test_17", testSchemaId(),
                List.of(new ColumnSpec("kod", "numeric", null, true), new ColumnSpec("ad", "text", null)));
        tableDataService.insertRow(table.getId(), Map.of("kod", 1, "ad", "x"));

        assertThrows(dbadmin.backend.exception.NotFoundException.class,
                () -> tableDataService.updateRow(table.getId(), Map.of("kod", 999), Map.of("ad", "y")));
    }

    @Test
    void updateRow_bilinmeyenKolon_validationExceptionFirlatir() {
        DataTable table = tableService.createTable("veri_test_18", testSchemaId(),
                List.of(new ColumnSpec("kod", "numeric", null, true)));
        tableDataService.insertRow(table.getId(), Map.of("kod", 1));

        assertThrows(ValidationException.class,
                () -> tableDataService.updateRow(table.getId(), Map.of("kod", 1), Map.of("olmayan", "x")));
    }

    /** requirement notu 8 ("CSV Export ekle -> minio'ya yazilacak") — icerik ve satir sayisi dogru mu. */
    @Test
    void exportCsv_gecerliTablo_csvIcerigiVeMinioAnahtariDoner() {
        DataTable table = tableService.createTable("veri_test_11", testSchemaId(),
                List.of(new ColumnSpec("ad", "text", null), new ColumnSpec("yas", "numeric", null)));
        jdbcTemplate.update(
                "INSERT INTO " + TEST_SCHEMA + ".veri_test_11 (ad, yas) VALUES ('Ayşe', 30), ('Kerem', 25)");

        TableDataService.CsvExportResult result = tableDataService.exportCsv(table.getId());

        assertEquals(2, result.rowCount());
        assertTrue(result.key().startsWith("csv-exports/"));
        assertTrue(result.key().endsWith(".csv"));
        assertEquals("data_test_sema_veri_test_11.csv", result.fileName());

        String csv = new String(result.content(), java.nio.charset.StandardCharsets.UTF_8);
        String[] lines = csv.split("\r\n");
        assertEquals("ad,yas", lines[0]);
        assertEquals("Ayşe,30", lines[1]);
        assertEquals("Kerem,25", lines[2]);
    }

    /** Deger virgul/tirnak iceriyorsa CSV'de tirnak icine alinip ic tirnaklar ikiye katlanmali. */
    @Test
    void exportCsv_virgulVeTirnakIcerenDeger_dogruEscapeEdilir() {
        DataTable table = tableService.createTable("veri_test_12", testSchemaId(),
                List.of(new ColumnSpec("aciklama", "text", null)));
        tableDataService.insertRow(table.getId(), Map.of("aciklama", "a, \"b\" c"));

        TableDataService.CsvExportResult result = tableDataService.exportCsv(table.getId());

        String csv = new String(result.content(), java.nio.charset.StandardCharsets.UTF_8);
        String[] lines = csv.split("\r\n");
        assertEquals("\"a, \"\"b\"\" c\"", lines[1]);
    }

    @Test
    void exportCsv_bosTablo_bosSatirListesiDoner() {
        DataTable table = tableService.createTable("veri_test_13", testSchemaId(),
                List.of(new ColumnSpec("ad", "text", null)));

        TableDataService.CsvExportResult result = tableDataService.exportCsv(table.getId());

        assertEquals(0, result.rowCount());
        String csv = new String(result.content(), java.nio.charset.StandardCharsets.UTF_8);
        assertEquals("ad\r\n", csv);
    }
}

package dbadmin.backend.controller;

import dbadmin.backend.dto.ChangePrimaryKeyRequest;
import dbadmin.backend.dto.ChangeTableSchemaRequest;
import dbadmin.backend.dto.ChangeTagRequest;
import dbadmin.backend.dto.ColumnResponse;
import dbadmin.backend.dto.ColumnUpdateRequest;
import dbadmin.backend.dto.CreateColumnRequest;
import dbadmin.backend.dto.CreateTableRequest;
import dbadmin.backend.dto.ErrorExamples;
import dbadmin.backend.dto.ErrorResponse;
import dbadmin.backend.dto.InsertRowRequest;
import dbadmin.backend.dto.RenameRequest;
import dbadmin.backend.dto.TableDataResponse;
import dbadmin.backend.dto.TableResponse;
import dbadmin.backend.dto.TableUpdateRequest;
import dbadmin.backend.dto.UpdateRowRequest;
import dbadmin.backend.service.ColumnSpec;
import dbadmin.backend.service.ColumnUpdate;
import dbadmin.backend.service.TableDataService;
import dbadmin.backend.service.TableService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Tablo ve Kolon icin HTTP endpoint'leri. {@code @RestController} donen degeri otomatik
 * JSON'a cevirir; {@code @RequestMapping} tum metodlar icin ortak "/api/tables" on-ekini
 * belirler. Burasi ince bir katman: dogrulama/is mantigi yok, hepsi {@link TableService}'te —
 * bu sinifin isi sadece HTTP <-> DTO <-> service cevirisi yapmak.
 */
@RestController
@RequestMapping("/api/tables")
@Tag(name = "Tablolar", description = "Tablo ve kolon metadata'sini yonetir; her yazma islemi ayni anda "
        + "gercek Postgres semasini da (CREATE/ALTER/DROP TABLE) degistirir.")
public class TableController {

    private final TableService tableService;
    private final TableDataService tableDataService;

    public TableController(TableService tableService, TableDataService tableDataService) {
        this.tableService = tableService;
        this.tableDataService = tableDataService;
    }

    /** GET /api/tables — tum tablolarin sayfalanmis listesi. Entity degil DTO ({@link TableResponse}) doner; bkz. dto paketi neden ayri. */
    @Operation(summary = "Tum tablolari sayfalanmis olarak listele",
            description = "Sistemdeki tum tablolari, her birinin kolonlariyla birlikte, sayfalanmis "
                    + "olarak doner. Filtreleme yapmaz — tek bir schema'nin tablolarini istiyorsan "
                    + "GET /api/schemas/{id}/tables kullan. Standart Spring parametreleri gecerlidir: "
                    + "page, size, sort (ör. sort=name,desc).")
    @ApiResponse(responseCode = "200", description = "Tablo sayfasi (icerik bos olabilir).")
    @GetMapping
    public Page<TableResponse> list(Pageable pageable) {
        return tableService.listTables(pageable).map(TableResponse::from);
    }

    /** GET /api/tables/{id} — tek bir tablonun detayi (kolonlariyla birlikte). */
    @Operation(summary = "Tek bir tabloyu getir", description = "Id'si verilen tablonun tum bilgilerini "
            + "(kolonlar dahil) doner.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Tablo bulundu."),
        @ApiResponse(responseCode = "404", description = "Bu id'de bir tablo yok.",
                content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                        schema = @Schema(implementation = ErrorResponse.class),
                        examples = @ExampleObject(name = "NOT_FOUND_TABLE",
                                summary = "Tablo bulunamadi",
                                value = ErrorExamples.NOT_FOUND_TABLE)))
    })
    @GetMapping("/{id}")
    public TableResponse get(@Parameter(description = "Tablonun id'si.", example = "1") @PathVariable Long id) {
        return TableResponse.from(tableService.getTable(id));
    }

    /**
     * GET /api/tables/{id}/n-plus-one-demo — SADECE OGRETICI/DEMO AMACLI, gercek API'nin bir
     * parcasi degil (Swagger'da da bilerek gizli tutuluyor, bkz. hidden=true). N+1 problemini
     * canli gostermek icin var: {@link TableService#listColumnsNPlusOneDemo} bilinclidir kolon
     * id'lerini tek sorguda cekip her birini AYRI bir sorguyla tekrar ceker. Bir tablonun 5
     * kolonu varsa toplam 6 sorgu (1 + 5) atilir.
     */
    @Operation(hidden = true)
    @GetMapping("/{id}/n-plus-one-demo")
    public List<ColumnResponse> nPlusOneDemo(@PathVariable Long id) {
        return tableService.listColumnsNPlusOneDemo(id).stream().map(ColumnResponse::from).toList();
    }

    /**
     * GET /api/tables/{id}/data — gercek Postgres tablosunun satirlarini sayfalanmis olarak
     * doner (requirement notu 7, "DBeaver'daki gibi Show Data"). Metadata (Tablo/Kolon) DEGIL,
     * {@code SELECT * FROM sema.tablo LIMIT ? OFFSET ?} calistirir — bkz. {@link TableDataService}.
     * {@code page}/{@code size} Spring'in standart {@code Pageable}'i DEGIL: {@code size} sabit
     * bir whitelist'e ({@code 20,50,100,200,500}) kisitli, cunku bu uc kullaniciya rastgele buyuk
     * bir LIMIT verme imkani taniyor (tum tabloyu tek istekte cekmeyi engellemek icin).
     */
    @Operation(summary = "Tablonun gercek satir verisini sayfalanmis olarak getirir",
            description = "Metadata degil, gercek Postgres tablosunda SELECT * calistirir. "
                    + "size sadece 20, 50, 100, 200, 500 degerlerinden birini kabul eder.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Sayfa verisi dondu."),
        @ApiResponse(responseCode = "400", description = "size whitelist disinda ya da page negatif.",
                content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                        schema = @Schema(implementation = ErrorResponse.class),
                        examples = @ExampleObject(name = "VALIDATION_INVALID_PAGE_SIZE",
                                summary = "Gecersiz sayfa boyutu",
                                value = ErrorExamples.VALIDATION_INVALID_PAGE_SIZE))),
        @ApiResponse(responseCode = "404", description = "Bu id'de bir tablo yok.",
                content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                        schema = @Schema(implementation = ErrorResponse.class),
                        examples = @ExampleObject(name = "NOT_FOUND_TABLE",
                                summary = "Tablo bulunamadi",
                                value = ErrorExamples.NOT_FOUND_TABLE)))
    })
    @GetMapping("/{id}/data")
    public TableDataResponse data(
            @Parameter(description = "Tablonun id'si.", example = "1") @PathVariable Long id,
            @Parameter(description = "0 tabanli sayfa numarasi.", example = "0")
                    @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Sayfa basina satir sayisi — 20, 50, 100, 200 ya da 500.", example = "20")
                    @RequestParam(defaultValue = "20") int size) {
        return tableDataService.getData(id, page, size);
    }

    /**
     * POST /api/tables/{id}/data — kullanicinin kendi satir verisini girmesi (requirement notu
     * 7'nin devami). Metadata degil, dogrudan gercek Postgres tablosuna INSERT yapar; DDL degil
     * DML oldugu icin {@code TableDataService} kullanilir, {@code ddl/} paketindeki executor'lar
     * degil.
     */
    @Operation(summary = "Tabloya yeni bir satir ekler",
            description = "Sadece tabloda gercekten var olan kolonlar kabul edilir. Verilmeyen "
                    + "kolonlar DB'nin varsayilanina (varsa) ya da NULL'a duser.")
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Satir eklendi."),
        @ApiResponse(responseCode = "400", description = "Bilinmeyen bir kolon adi gonderildi ya da hicbir deger verilmedi.",
                content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                        schema = @Schema(implementation = ErrorResponse.class),
                        examples = @ExampleObject(name = "VALIDATION_UNKNOWN_COLUMN",
                                summary = "Boyle bir kolon yok",
                                value = ErrorExamples.VALIDATION_UNKNOWN_COLUMN))),
        @ApiResponse(responseCode = "404", description = "Bu id'de bir tablo yok.",
                content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                        schema = @Schema(implementation = ErrorResponse.class),
                        examples = @ExampleObject(name = "NOT_FOUND_TABLE",
                                summary = "Tablo bulunamadi",
                                value = ErrorExamples.NOT_FOUND_TABLE))),
        @ApiResponse(responseCode = "409", description = "PRIMARY KEY/NOT NULL gibi bir Postgres kisiti ihlal edildi.",
                content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                        schema = @Schema(implementation = ErrorResponse.class)))
    })
    // 201 DEGIL 204: govde donmuyor (olusan satirin bir "representation"i yok, sayfayi
    // TableDetail.tsx zaten yeniden cekiyor) — client.ts'teki request() SADECE 204'te body
    // parse etmeyi atlar (bkz. javadoc'u), 201+bos-govde kombinasyonu response.json()'i
    // "Unexpected end of JSON input" ile patlatirdi (kod GERCEKTEN eklenmis olsa bile
    // frontend'e "basarisiz" gibi gorunurdu — bu yuzden deleteColumn/delete ile ayni desen).
    @PostMapping("/{id}/data")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void insertRow(
            @Parameter(description = "Tablonun id'si.", example = "1") @PathVariable Long id,
            @RequestBody InsertRowRequest request) {
        tableDataService.insertRow(id, request.values());
    }

    /**
     * PATCH /api/tables/{id}/data — var olan bir satirin duzenlenmesi (requirement notu 7'nin
     * devami, "veri duzenleme"). Satir {@code pk} ile bulunur, sadece {@code values}'taki
     * kolonlar guncellenir; PK kolonlari degistirilemez (bkz. {@link TableDataService#updateRow}).
     */
    @Operation(summary = "Var olan bir satiri gunceller",
            description = "Satir, tablonun PRIMARY KEY kolonlariyla (pk) bulunur. PK'siz bir "
                    + "tabloda satir tekil olarak duzenlenemez (400). PK kolonlari values icinde olamaz.")
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Satir guncellendi."),
        @ApiResponse(responseCode = "400", description = "PK eksik/yanlis, bilinmeyen kolon, PK degistirilmeye "
                + "calisildi ya da tabloda PRIMARY KEY yok.",
                content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                        schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "404", description = "Bu id'de bir tablo yok, ya da pk'ya uyan bir satir yok.",
                content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                        schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "409", description = "PRIMARY KEY/NOT NULL/UNIQUE gibi bir Postgres kisiti ihlal edildi.",
                content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                        schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PatchMapping("/{id}/data")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void updateRow(
            @Parameter(description = "Tablonun id'si.", example = "1") @PathVariable Long id,
            @RequestBody UpdateRowRequest request) {
        tableDataService.updateRow(id, request.pk(), request.values());
    }

    /**
     * GET /api/tables/{id}/data/csv-export — requirement notu 8 ("CSV Export ekle -> minio'ya
     * yazilacak"). {@code getData}'nin aksine sayfalanmis DEGIL, tum satirlar tek dosyada.
     * {@link TableDataService#exportCsv} dosyayi hem MinIO'ya yazar (kalici kopya/izlenebilirlik)
     * hem de burada donen byte[] ile ayni istekte tarayiciya indirilir.
     */
    @Operation(summary = "Tablonun tum verisini CSV olarak disari aktarir",
            description = "Sayfalama yok, tablonun tum satirlari tek bir CSV dosyasina yazilir. "
                    + "Dosya MinIO'ya yuklenir ve ayni yanitla tarayiciya da indirilir.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "CSV dosyasi dondu.",
                content = @Content(mediaType = "text/csv")),
        @ApiResponse(responseCode = "404", description = "Bu id'de bir tablo yok.",
                content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                        schema = @Schema(implementation = ErrorResponse.class),
                        examples = @ExampleObject(name = "NOT_FOUND_TABLE",
                                summary = "Tablo bulunamadi",
                                value = ErrorExamples.NOT_FOUND_TABLE)))
    })
    @GetMapping("/{id}/data/csv-export")
    public ResponseEntity<byte[]> exportCsv(
            @Parameter(description = "Tablonun id'si.", example = "1") @PathVariable Long id) {
        TableDataService.CsvExportResult result = tableDataService.exportCsv(id);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("text/csv"))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + result.fileName() + "\"")
                .body(result.content());
    }

    /**
     * POST /api/tables — yeni tablo olusturur. {@code @ResponseStatus(CREATED)} basarili
     * sonucta 200 degil 201 dondurur (REST konvansiyonu: "yeni kaynak yaratildi").
     * Request'teki kolon listesi burada DTO'dan ({@link CreateColumnRequest}) service'in
     * bekledigi ic tipe ({@link ColumnSpec}) cevriliyor.
     */
    @Operation(summary = "Yeni tablo olustur",
            description = "Verilen isim ve kolon listesiyle hem metadata satirini hem de gercek Postgres "
                    + "tablosunu olusturur. Gercek tabloda sadece burada verilen kolonlar bulunur — otomatik "
                    + "eklenen 'id' kolonu yoktur. En az bir kolon zorunlu. schemaId de zorunlu — tablonun "
                    + "kurulacagi schema acikca verilmeli. primaryKey=true isaretlenen kolonlarin tamami "
                    + "tablonun PRIMARY KEY'ini olusturur; birden fazlasi isaretlenirse composite primary key "
                    + "kurulur: PRIMARY KEY (kolon1, kolon2).")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Tablo olusturuldu."),
        @ApiResponse(responseCode = "400",
                description = "Gecersiz tablo/kolon adi, gecersiz kolon tipi, hic kolon verilmemis, "
                        + "ya da schemaId bos birakilmis.",
                content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                        schema = @Schema(implementation = ErrorResponse.class),
                        examples = {
                            @ExampleObject(name = "VALIDATION_INVALID_TABLE_NAME",
                                    summary = "Tablo adi kurallara uymuyor",
                                    value = ErrorExamples.VALIDATION_INVALID_TABLE_NAME),
                            @ExampleObject(name = "VALIDATION_INVALID_COLUMN_NAME",
                                    summary = "Kolon adi kurallara uymuyor",
                                    value = ErrorExamples.VALIDATION_INVALID_COLUMN_NAME),
                            @ExampleObject(name = "VALIDATION_INVALID_COLUMN_TYPE",
                                    summary = "Kolon tipi whitelist disinda",
                                    value = ErrorExamples.VALIDATION_INVALID_COLUMN_TYPE),
                            @ExampleObject(name = "VALIDATION_TABLE_NEEDS_COLUMN",
                                    summary = "Kolon listesi bos",
                                    value = ErrorExamples.VALIDATION_TABLE_NEEDS_COLUMN),
                            @ExampleObject(name = "VALIDATION_MISSING_SCHEMA",
                                    summary = "schemaId gonderilmemis",
                                    value = ErrorExamples.VALIDATION_MISSING_SCHEMA)
                        })),
        @ApiResponse(responseCode = "404", description = "Belirtilen schemaId'de bir schema yok "
                + "(gizli 'public' schema'sinin id'si de buraya girer), ya da kolona verilen tagId yok.",
                content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                        schema = @Schema(implementation = ErrorResponse.class),
                        examples = {
                            @ExampleObject(name = "NOT_FOUND_SCHEMA",
                                    summary = "Schema bulunamadi",
                                    value = ErrorExamples.NOT_FOUND_SCHEMA),
                            @ExampleObject(name = "NOT_FOUND_TAG",
                                    summary = "Etiket bulunamadi",
                                    value = ErrorExamples.NOT_FOUND_TAG)
                        })),
        @ApiResponse(responseCode = "409",
                description = "Bu isimde bir tablo zaten var, ya da request'te ayni isimde iki kolon var.",
                content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                        schema = @Schema(implementation = ErrorResponse.class),
                        examples = {
                            @ExampleObject(name = "CONFLICT_DUPLICATE_TABLE_NAME",
                                    summary = "Ayni isimde tablo var",
                                    value = ErrorExamples.CONFLICT_DUPLICATE_TABLE_NAME),
                            @ExampleObject(name = "CONFLICT_DUPLICATE_COLUMN_IN_REQUEST",
                                    summary = "Request'te ayni kolon adi iki kez",
                                    value = ErrorExamples.CONFLICT_DUPLICATE_COLUMN_IN_REQUEST)
                        }))
    })
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TableResponse create(@RequestBody CreateTableRequest request) {
        List<ColumnSpec> columnSpecs = request.columns() == null
                ? List.of()
                : request.columns().stream().map(CreateColumnRequest::toColumnSpec).toList();
        return TableResponse.from(tableService.createTable(request.name(), request.schemaId(), columnSpecs));
    }

    /** PATCH /api/tables/{id} — sadece ismi degistirir (PATCH = kismi guncelleme, PUT gibi tum kaynagi degistirmez). */
    @Operation(summary = "Tabloyu yeniden adlandir",
            description = "Hem metadata'daki hem gercek Postgres tablosunun adini degistirir "
                    + "(ALTER TABLE ... RENAME TO). Tablonun kolonlarina dokunmaz.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Tablo yeniden adlandirildi."),
        @ApiResponse(responseCode = "400", description = "Gecersiz yeni isim.",
                content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                        schema = @Schema(implementation = ErrorResponse.class),
                        examples = @ExampleObject(name = "VALIDATION_INVALID_TABLE_NAME",
                                summary = "Tablo adi kurallara uymuyor",
                                value = ErrorExamples.VALIDATION_INVALID_TABLE_NAME))),
        @ApiResponse(responseCode = "404", description = "Bu id'de bir tablo yok.",
                content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                        schema = @Schema(implementation = ErrorResponse.class),
                        examples = @ExampleObject(name = "NOT_FOUND_TABLE",
                                summary = "Tablo bulunamadi",
                                value = ErrorExamples.NOT_FOUND_TABLE))),
        @ApiResponse(responseCode = "409", description = "Bu isimde baska bir tablo zaten var.",
                content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                        schema = @Schema(implementation = ErrorResponse.class),
                        examples = @ExampleObject(name = "CONFLICT_DUPLICATE_TABLE_NAME",
                                summary = "Ayni isimde tablo var",
                                value = ErrorExamples.CONFLICT_DUPLICATE_TABLE_NAME)))
    })
    @PatchMapping("/{id}")
    public TableResponse rename(
            @Parameter(description = "Yeniden adlandirilacak tablonun id'si.", example = "1") @PathVariable Long id,
            @RequestBody RenameRequest request) {
        return TableResponse.from(tableService.renameTable(id, request.name()));
    }

    /** PATCH /api/tables/{id}/schema — tabloyu baska bir schema'ya tasir. */
    @Operation(summary = "Tabloyu baska bir schema'ya tasi",
            description = "Hem metadata'daki hem gercek Postgres tablosunun schema'sini degistirir "
                    + "(ALTER TABLE ... SET SCHEMA). schemaId zorunlu — bos gecilemez.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Tablo tasindi (ya da zaten o schema'daysa, "
                + "hicbir sey degismeden ayni tablo geri doner)."),
        @ApiResponse(responseCode = "400", description = "schemaId bos gecildi.",
                content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                        schema = @Schema(implementation = ErrorResponse.class),
                        examples = @ExampleObject(name = "VALIDATION_MISSING_SCHEMA",
                                summary = "schemaId gonderilmemis",
                                value = ErrorExamples.VALIDATION_MISSING_SCHEMA))),
        @ApiResponse(responseCode = "404", description = "Tablo ya da hedef schema bulunamadi.",
                content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                        schema = @Schema(implementation = ErrorResponse.class),
                        examples = {
                            @ExampleObject(name = "NOT_FOUND_TABLE",
                                    summary = "Tasinacak tablo bulunamadi",
                                    value = ErrorExamples.NOT_FOUND_TABLE),
                            @ExampleObject(name = "NOT_FOUND_SCHEMA",
                                    summary = "Hedef schema bulunamadi",
                                    value = ErrorExamples.NOT_FOUND_SCHEMA)
                        }))
    })
    @PatchMapping("/{id}/schema")
    public TableResponse changeSchema(
            @Parameter(description = "Tasinacak tablonun id'si.", example = "1") @PathVariable Long id,
            @RequestBody ChangeTableSchemaRequest request) {
        return TableResponse.from(tableService.changeSchema(id, request.schemaId()));
    }

    /** DELETE /api/tables/{id} — tablo ve kolonlarini siler. Govde donmedigi icin 204 No Content. */
    @Operation(summary = "Tabloyu sil",
            description = "Tabloyu ve altindaki tum kolonlarin metadata'sini siler, ardindan gercek "
                    + "Postgres tablosunu da DROP TABLE ile siler. Geri alinamaz.")
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Tablo silindi."),
        @ApiResponse(responseCode = "404", description = "Bu id'de bir tablo yok.",
                content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                        schema = @Schema(implementation = ErrorResponse.class),
                        examples = @ExampleObject(name = "NOT_FOUND_TABLE",
                                summary = "Tablo bulunamadi",
                                value = ErrorExamples.NOT_FOUND_TABLE)))
    })
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@Parameter(description = "Silinecek tablonun id'si.", example = "1") @PathVariable Long id) {
        tableService.deleteTable(id);
    }

    /** POST /api/tables/{id}/columns — var olan tabloya yeni kolon ekler. */
    @Operation(summary = "Tabloya yeni kolon ekle",
            description = "Var olan bir tabloya hem metadata satiri hem gercek Postgres kolonu "
                    + "(ALTER TABLE ... ADD COLUMN) ekler. primaryKey=true gonderilirse tablonun PRIMARY KEY'i "
                    + "bu yeni kolonu da icerecek sekilde yeniden kurulur (composite PK). Dikkat: tabloda "
                    + "zaten satir varsa yeni kolon o satirlarda bos (NULL) olacagi icin PRIMARY KEY "
                    + "kurulamaz ve istek hata verir.")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Kolon eklendi."),
        @ApiResponse(responseCode = "400", description = "Gecersiz kolon adi ya da gecersiz kolon tipi.",
                content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                        schema = @Schema(implementation = ErrorResponse.class),
                        examples = {
                            @ExampleObject(name = "VALIDATION_INVALID_COLUMN_NAME",
                                    summary = "Kolon adi kurallara uymuyor",
                                    value = ErrorExamples.VALIDATION_INVALID_COLUMN_NAME),
                            @ExampleObject(name = "VALIDATION_INVALID_COLUMN_TYPE",
                                    summary = "Kolon tipi whitelist disinda",
                                    value = ErrorExamples.VALIDATION_INVALID_COLUMN_TYPE)
                        })),
        @ApiResponse(responseCode = "404", description = "Tablo ya da verilen tagId'de bir etiket bulunamadi.",
                content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                        schema = @Schema(implementation = ErrorResponse.class),
                        examples = {
                            @ExampleObject(name = "NOT_FOUND_TABLE",
                                    summary = "Tablo bulunamadi",
                                    value = ErrorExamples.NOT_FOUND_TABLE),
                            @ExampleObject(name = "NOT_FOUND_TAG",
                                    summary = "Etiket bulunamadi",
                                    value = ErrorExamples.NOT_FOUND_TAG)
                        })),
        @ApiResponse(responseCode = "409", description = "Bu tabloda ayni isimde bir kolon zaten var.",
                content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                        schema = @Schema(implementation = ErrorResponse.class),
                        examples = @ExampleObject(name = "CONFLICT_DUPLICATE_COLUMN_NAME",
                                summary = "Ayni isimde kolon var",
                                value = ErrorExamples.CONFLICT_DUPLICATE_COLUMN_NAME)))
    })
    @PostMapping("/{id}/columns")
    @ResponseStatus(HttpStatus.CREATED)
    public ColumnResponse addColumn(
            @Parameter(description = "Kolonun ekleneceği tablonun id'si.", example = "1") @PathVariable Long id,
            @RequestBody CreateColumnRequest request) {
        return ColumnResponse.from(tableService.addColumn(id, request.toColumnSpec()));
    }

    /** DELETE /api/tables/{id}/columns/{columnId} — tek bir kolonu siler. */
    @Operation(summary = "Kolonu sil",
            description = "Kolonun metadata satirini ve gercek Postgres kolonunu (ALTER TABLE ... DROP "
                    + "COLUMN) siler. Kolon PK-isaretliyse tablonun PRIMARY KEY'i kalan isaretli "
                    + "kolonlarla yeniden kurulur; hic kalmazsa tablo primary key'siz kalir. Kolonun "
                    + "bagli oldugu Tag silinmez.")
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Kolon silindi."),
        @ApiResponse(responseCode = "404", description = "Tablo ya da bu tabloda boyle bir kolon yok.",
                content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                        schema = @Schema(implementation = ErrorResponse.class),
                        examples = {
                            @ExampleObject(name = "NOT_FOUND_TABLE",
                                    summary = "Tablo bulunamadi",
                                    value = ErrorExamples.NOT_FOUND_TABLE),
                            @ExampleObject(name = "NOT_FOUND_COLUMN",
                                    summary = "Kolon bu tabloda yok",
                                    value = ErrorExamples.NOT_FOUND_COLUMN)
                        }))
    })
    @DeleteMapping("/{id}/columns/{columnId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteColumn(
            @Parameter(description = "Tablonun id'si.", example = "1") @PathVariable Long id,
            @Parameter(description = "Silinecek kolonun id'si.", example = "5") @PathVariable Long columnId) {
        tableService.deleteColumn(id, columnId);
    }

    /** PATCH .../name — kolonun adini degistirir (tipi degil, tip olusturulduktan sonra sabit). */
    @Operation(summary = "Kolonu yeniden adlandir",
            description = "Hem metadata'daki hem gercek Postgres kolonunun adini degistirir "
                    + "(ALTER TABLE ... RENAME COLUMN). Kolonun tipi degistirilemez.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Kolon yeniden adlandirildi."),
        @ApiResponse(responseCode = "400", description = "Gecersiz yeni isim.",
                content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                        schema = @Schema(implementation = ErrorResponse.class),
                        examples = @ExampleObject(name = "VALIDATION_INVALID_COLUMN_NAME",
                                summary = "Kolon adi kurallara uymuyor",
                                value = ErrorExamples.VALIDATION_INVALID_COLUMN_NAME))),
        @ApiResponse(responseCode = "404", description = "Tablo ya da bu tabloda boyle bir kolon yok.",
                content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                        schema = @Schema(implementation = ErrorResponse.class),
                        examples = {
                            @ExampleObject(name = "NOT_FOUND_TABLE",
                                    summary = "Tablo bulunamadi",
                                    value = ErrorExamples.NOT_FOUND_TABLE),
                            @ExampleObject(name = "NOT_FOUND_COLUMN",
                                    summary = "Kolon bu tabloda yok",
                                    value = ErrorExamples.NOT_FOUND_COLUMN)
                        })),
        @ApiResponse(responseCode = "409", description = "Bu tabloda ayni isimde baska bir kolon zaten var.",
                content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                        schema = @Schema(implementation = ErrorResponse.class),
                        examples = @ExampleObject(name = "CONFLICT_DUPLICATE_COLUMN_NAME",
                                summary = "Ayni isimde kolon var",
                                value = ErrorExamples.CONFLICT_DUPLICATE_COLUMN_NAME)))
    })
    @PatchMapping("/{id}/columns/{columnId}/name")
    public ColumnResponse renameColumn(
            @Parameter(description = "Tablonun id'si.", example = "1") @PathVariable Long id,
            @Parameter(description = "Yeniden adlandirilacak kolonun id'si.", example = "5") @PathVariable
                    Long columnId,
            @RequestBody RenameRequest request) {
        return ColumnResponse.from(tableService.renameColumn(id, columnId, request.name()));
    }

    /** PATCH .../tag — kolonun etiketini degistirir/kaldirir (tagId null gonderilirse etiket kaldirilir). */
    @Operation(summary = "Kolonun etiketini degistir",
            description = "Kolonu verilen etikete baglar; tagId null gonderilirse kolondaki mevcut etiket "
                    + "kaldirilir. Tag'in kendisi gercek Postgres semasinda bir karsiligi olmadigi icin "
                    + "(sadece metadata) bu islem DDL calistirmaz.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Etiket guncellendi."),
        @ApiResponse(responseCode = "404", description = "Tablo, kolon ya da belirtilen tagId'de bir "
                + "etiket bulunamadi.",
                content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                        schema = @Schema(implementation = ErrorResponse.class),
                        examples = {
                            @ExampleObject(name = "NOT_FOUND_TABLE",
                                    summary = "Tablo bulunamadi",
                                    value = ErrorExamples.NOT_FOUND_TABLE),
                            @ExampleObject(name = "NOT_FOUND_COLUMN",
                                    summary = "Kolon bu tabloda yok",
                                    value = ErrorExamples.NOT_FOUND_COLUMN),
                            @ExampleObject(name = "NOT_FOUND_TAG",
                                    summary = "Etiket bulunamadi",
                                    value = ErrorExamples.NOT_FOUND_TAG)
                        }))
    })
    @PatchMapping("/{id}/columns/{columnId}/tag")
    public ColumnResponse changeColumnTag(
            @Parameter(description = "Tablonun id'si.", example = "1") @PathVariable Long id,
            @Parameter(description = "Etiketi degistirilecek kolonun id'si.", example = "5") @PathVariable
                    Long columnId,
            @RequestBody ChangeTagRequest request) {
        return ColumnResponse.from(tableService.changeColumnTag(id, columnId, request.tagId()));
    }

    /** PATCH .../primary-key — var olan bir kolonu tablonun PRIMARY KEY'ine ekler/cikarir. */
    @Operation(summary = "Kolonun birincil anahtar isaretini degistir",
            description = "Var olan bir kolonu tablonun PRIMARY KEY'ine ekler (primaryKey=true) ya da "
                    + "cikarir (false). Tablonun gercek PRIMARY KEY constraint'i her seferinde guncel "
                    + "isaretli kolon setiyle yeniden kurulur: birden fazla isaretli kolon varsa composite "
                    + "PK olur, hic kalmazsa tablo primary key'siz kalir. Diger uclardan farki, buranin "
                    + "kolon olusturulduktan sonra da calisabilmesi — eskiden isaret yalnizca kolon "
                    + "olustururken verilebiliyordu.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Isaret guncellendi."),
        @ApiResponse(responseCode = "404", description = "Tablo ya da kolon bulunamadi.",
                content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                        schema = @Schema(implementation = ErrorResponse.class),
                        examples = {
                            @ExampleObject(name = "NOT_FOUND_TABLE",
                                    summary = "Tablo bulunamadi",
                                    value = ErrorExamples.NOT_FOUND_TABLE),
                            @ExampleObject(name = "NOT_FOUND_COLUMN",
                                    summary = "Kolon bu tabloda yok",
                                    value = ErrorExamples.NOT_FOUND_COLUMN)
                        })),
        @ApiResponse(responseCode = "409",
                description = "Kolon PRIMARY KEY yapilamadi: tabloda o kolonu bos (NULL) olan ya da "
                        + "tekrar eden degerler iceren satirlar var. Metadata degismez, islem geri alinir.",
                content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                        schema = @Schema(implementation = ErrorResponse.class),
                        examples = @ExampleObject(name = "CONFLICT_COLUMN_NOT_UNIQUE",
                                summary = "Kolonda NULL/tekrar eden deger var",
                                value = ErrorExamples.CONFLICT_COLUMN_NOT_UNIQUE)))
    })
    @PatchMapping("/{id}/columns/{columnId}/primary-key")
    public ColumnResponse changeColumnPrimaryKey(
            @Parameter(description = "Tablonun id'si.", example = "1") @PathVariable Long id,
            @Parameter(description = "Isareti degistirilecek kolonun id'si.", example = "5") @PathVariable
                    Long columnId,
            @RequestBody ChangePrimaryKeyRequest request) {
        boolean primaryKey = request.primaryKey() != null && request.primaryKey();
        return ColumnResponse.from(tableService.changeColumnPrimaryKey(id, columnId, primaryKey));
    }

    /**
     * PATCH .../changes — bir tablo uzerinde biriktirilmis tum degisiklikleri (isim, schema,
     * silinen/eklenen/guncellenen kolonlar) tek istekte, tek transaction'da uygular.
     * Frontend'deki "istediginiz kadar duzenleme yapin, en son Kaydet'e basinca hepsi birden
     * gitsin" akisinin backend karsiligi budur — diger tekli uclarin (rename/tag/PK/vs.) aksine,
     * burada bir alt-islem patlarsa hicbir alt-islem kalici olmaz.
     */
    @Operation(summary = "Tablo uzerindeki biriktirilmis degisiklikleri tek seferde uygula",
            description = "Isim, schema, silinecek kolonlar, eklenecek kolonlar ve guncellenecek "
                    + "kolonlar TEK bir istekte gonderilir ve TEK bir transaction icinde uygulanir: "
                    + "herhangi bir alt-islem (ör. bir kolonu PK yapmak NULL degerler yuzunden "
                    + "reddedilirse) basarisiz olursa, o ana kadar uygulanmis diger hicbir "
                    + "degisiklik (rename, silme, ekleme) de kalici olmaz. newName/newSchemaId "
                    + "null gecilirse o alana dokunulmaz; columnsToUpdate'daki her satir "
                    + "kolonun nihai (isim/tag/PK) halini tasir.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Tum degisiklikler uygulandi, guncel tablo doner."),
        @ApiResponse(responseCode = "400",
                description = "Gecersiz yeni tablo/kolon adi, gecersiz kolon tipi, ya da eklenecek "
                        + "kolonlar listesinde ayni isimde iki kolon var.",
                content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                        schema = @Schema(implementation = ErrorResponse.class),
                        examples = {
                            @ExampleObject(name = "VALIDATION_INVALID_TABLE_NAME",
                                    summary = "Tablo adi kurallara uymuyor",
                                    value = ErrorExamples.VALIDATION_INVALID_TABLE_NAME),
                            @ExampleObject(name = "VALIDATION_INVALID_COLUMN_NAME",
                                    summary = "Kolon adi kurallara uymuyor",
                                    value = ErrorExamples.VALIDATION_INVALID_COLUMN_NAME)
                        })),
        @ApiResponse(responseCode = "404", description = "Tablo, hedef schema, bir kolon ya da "
                + "bir tag bulunamadi.",
                content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                        schema = @Schema(implementation = ErrorResponse.class),
                        examples = {
                            @ExampleObject(name = "NOT_FOUND_TABLE",
                                    summary = "Tablo bulunamadi",
                                    value = ErrorExamples.NOT_FOUND_TABLE),
                            @ExampleObject(name = "NOT_FOUND_SCHEMA",
                                    summary = "Hedef schema bulunamadi",
                                    value = ErrorExamples.NOT_FOUND_SCHEMA),
                            @ExampleObject(name = "NOT_FOUND_COLUMN",
                                    summary = "Guncellenecek/silinecek kolon bulunamadi",
                                    value = ErrorExamples.NOT_FOUND_COLUMN),
                            @ExampleObject(name = "NOT_FOUND_TAG",
                                    summary = "Etiket bulunamadi",
                                    value = ErrorExamples.NOT_FOUND_TAG)
                        })),
        @ApiResponse(responseCode = "409",
                description = "Yeni isim baska bir tablo/kolonla cakisiyor, ya da bir kolonu "
                        + "PRIMARY KEY yapmak tabloda NULL/tekrar eden deger oldugu icin reddedildi.",
                content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                        schema = @Schema(implementation = ErrorResponse.class),
                        examples = {
                            @ExampleObject(name = "CONFLICT_DUPLICATE_TABLE_NAME",
                                    summary = "Ayni isimde tablo var",
                                    value = ErrorExamples.CONFLICT_DUPLICATE_TABLE_NAME),
                            @ExampleObject(name = "CONFLICT_DUPLICATE_COLUMN_NAME",
                                    summary = "Ayni isimde kolon var",
                                    value = ErrorExamples.CONFLICT_DUPLICATE_COLUMN_NAME),
                            @ExampleObject(name = "CONFLICT_COLUMN_NOT_UNIQUE",
                                    summary = "Kolonda NULL/tekrar eden deger var",
                                    value = ErrorExamples.CONFLICT_COLUMN_NOT_UNIQUE)
                        }))
    })
    @PatchMapping("/{id}/changes")
    public TableResponse applyChanges(
            @Parameter(description = "Tablonun id'si.", example = "1") @PathVariable Long id,
            @RequestBody TableUpdateRequest request) {
        List<ColumnSpec> columnsToAdd = request.columnsToAddOrEmpty().stream()
                .map(CreateColumnRequest::toColumnSpec)
                .toList();
        List<ColumnUpdate> columnsToUpdate = request.columnsToUpdateOrEmpty().stream()
                .map(u -> new ColumnUpdate(u.columnId(), u.newName(), u.newTagId(), u.newPrimaryKey()))
                .toList();
        return TableResponse.from(tableService.applyChanges(id, request.newName(), request.newSchemaId(),
                request.columnIdsToDeleteOrEmpty(), columnsToAdd, columnsToUpdate));
    }
}

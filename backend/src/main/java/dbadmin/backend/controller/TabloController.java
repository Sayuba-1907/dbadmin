package dbadmin.backend.controller;

import dbadmin.backend.dto.ChangePrimaryKeyRequest;
import dbadmin.backend.dto.ChangeTabloSchemaRequest;
import dbadmin.backend.dto.ChangeTagRequest;
import dbadmin.backend.dto.CreateKolonRequest;
import dbadmin.backend.dto.CreateTabloRequest;
import dbadmin.backend.dto.ErrorExamples;
import dbadmin.backend.dto.ErrorResponse;
import dbadmin.backend.dto.KolonGuncellemeRequest;
import dbadmin.backend.dto.KolonResponse;
import dbadmin.backend.dto.RenameRequest;
import dbadmin.backend.dto.TabloResponse;
import dbadmin.backend.dto.TabloUpdateRequest;
import dbadmin.backend.service.KolonGuncelleme;
import dbadmin.backend.service.KolonTanimi;
import dbadmin.backend.service.TabloService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Tablo ve Kolon icin HTTP endpoint'leri. {@code @RestController} donen degeri otomatik
 * JSON'a cevirir; {@code @RequestMapping} tum metodlar icin ortak "/api/tablolar" on-ekini
 * belirler. Burasi ince bir katman: dogrulama/is mantigi yok, hepsi {@link TabloService}'te —
 * bu sinifin isi sadece HTTP <-> DTO <-> service cevirisi yapmak.
 */
@RestController
@RequestMapping("/api/tablolar")
@Tag(name = "Tablolar", description = "Tablo ve kolon metadata'sini yonetir; her yazma islemi ayni anda "
        + "gercek Postgres semasini da (CREATE/ALTER/DROP TABLE) degistirir.")
public class TabloController {

    private final TabloService tabloService;

    public TabloController(TabloService tabloService) {
        this.tabloService = tabloService;
    }

    /** GET /api/tablolar — tum tablolarin listesi. Entity degil DTO ({@link TabloResponse}) doner; bkz. dto paketi neden ayri. */
    @Operation(summary = "Tum tablolari listele",
            description = "Sistemdeki tum tablolari, her birinin kolonlariyla birlikte doner. "
                    + "Filtreleme yapmaz — tek bir schema'nin tablolarini istiyorsan "
                    + "GET /api/schemalar/{id}/tablolar kullan.")
    @ApiResponse(responseCode = "200", description = "Tablo listesi (bos olabilir).")
    @GetMapping
    public List<TabloResponse> list() {
        return tabloService.listTablolar().stream()
                .map(TabloResponse::from)
                .toList();
    }

    /** GET /api/tablolar/{id} — tek bir tablonun detayi (kolonlariyla birlikte). */
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
    public TabloResponse get(@Parameter(description = "Tablonun id'si.", example = "1") @PathVariable Long id) {
        return TabloResponse.from(tabloService.getTablo(id));
    }

    /**
     * POST /api/tablolar — yeni tablo olusturur. {@code @ResponseStatus(CREATED)} basarili
     * sonucta 200 degil 201 dondurur (REST konvansiyonu: "yeni kaynak yaratildi").
     * Request'teki kolon listesi burada DTO'dan ({@link CreateKolonRequest}) service'in
     * bekledigi ic tipe ({@link KolonTanimi}) cevriliyor.
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
    public TabloResponse create(@RequestBody CreateTabloRequest request) {
        List<KolonTanimi> kolonTanimlari = request.kolonlar() == null
                ? List.of()
                : request.kolonlar().stream().map(CreateKolonRequest::toKolonTanimi).toList();
        return TabloResponse.from(tabloService.createTablo(request.name(), request.schemaId(), kolonTanimlari));
    }

    /** PATCH /api/tablolar/{id} — sadece ismi degistirir (PATCH = kismi guncelleme, PUT gibi tum kaynagi degistirmez). */
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
    public TabloResponse rename(
            @Parameter(description = "Yeniden adlandirilacak tablonun id'si.", example = "1") @PathVariable Long id,
            @RequestBody RenameRequest request) {
        return TabloResponse.from(tabloService.renameTablo(id, request.name()));
    }

    /** PATCH /api/tablolar/{id}/schema — tabloyu baska bir schema'ya tasir. */
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
    public TabloResponse changeSchema(
            @Parameter(description = "Tasinacak tablonun id'si.", example = "1") @PathVariable Long id,
            @RequestBody ChangeTabloSchemaRequest request) {
        return TabloResponse.from(tabloService.changeSchema(id, request.schemaId()));
    }

    /** DELETE /api/tablolar/{id} — tablo ve kolonlarini siler. Govde donmedigi icin 204 No Content. */
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
        tabloService.deleteTablo(id);
    }

    /** POST /api/tablolar/{id}/kolonlar — var olan tabloya yeni kolon ekler. */
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
    @PostMapping("/{id}/kolonlar")
    @ResponseStatus(HttpStatus.CREATED)
    public KolonResponse addKolon(
            @Parameter(description = "Kolonun ekleneceği tablonun id'si.", example = "1") @PathVariable Long id,
            @RequestBody CreateKolonRequest request) {
        return KolonResponse.from(tabloService.addKolon(id, request.toKolonTanimi()));
    }

    /** DELETE /api/tablolar/{id}/kolonlar/{kolonId} — tek bir kolonu siler. */
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
    @DeleteMapping("/{id}/kolonlar/{kolonId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteKolon(
            @Parameter(description = "Tablonun id'si.", example = "1") @PathVariable Long id,
            @Parameter(description = "Silinecek kolonun id'si.", example = "5") @PathVariable Long kolonId) {
        tabloService.deleteKolon(id, kolonId);
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
    @PatchMapping("/{id}/kolonlar/{kolonId}/name")
    public KolonResponse renameKolon(
            @Parameter(description = "Tablonun id'si.", example = "1") @PathVariable Long id,
            @Parameter(description = "Yeniden adlandirilacak kolonun id'si.", example = "5") @PathVariable
                    Long kolonId,
            @RequestBody RenameRequest request) {
        return KolonResponse.from(tabloService.renameKolon(id, kolonId, request.name()));
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
    @PatchMapping("/{id}/kolonlar/{kolonId}/tag")
    public KolonResponse changeKolonTag(
            @Parameter(description = "Tablonun id'si.", example = "1") @PathVariable Long id,
            @Parameter(description = "Etiketi degistirilecek kolonun id'si.", example = "5") @PathVariable
                    Long kolonId,
            @RequestBody ChangeTagRequest request) {
        return KolonResponse.from(tabloService.changeKolonTag(id, kolonId, request.tagId()));
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
    @PatchMapping("/{id}/kolonlar/{kolonId}/primary-key")
    public KolonResponse changeKolonPrimaryKey(
            @Parameter(description = "Tablonun id'si.", example = "1") @PathVariable Long id,
            @Parameter(description = "Isareti degistirilecek kolonun id'si.", example = "5") @PathVariable
                    Long kolonId,
            @RequestBody ChangePrimaryKeyRequest request) {
        boolean primaryKey = request.primaryKey() != null && request.primaryKey();
        return KolonResponse.from(tabloService.changeKolonPrimaryKey(id, kolonId, primaryKey));
    }

    /**
     * PATCH .../degisiklikler — bir tablo uzerinde biriktirilmis tum degisiklikleri (isim, schema,
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
                    + "degisiklik (rename, silme, ekleme) de kalici olmaz. yeniIsim/yeniSchemaId "
                    + "null gecilirse o alana dokunulmaz; guncellenecekKolonlar'daki her satir "
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
    @PatchMapping("/{id}/degisiklikler")
    public TabloResponse applyChanges(
            @Parameter(description = "Tablonun id'si.", example = "1") @PathVariable Long id,
            @RequestBody TabloUpdateRequest request) {
        List<KolonTanimi> eklenecekKolonlar = request.eklenecekKolonlarVeyaBos().stream()
                .map(CreateKolonRequest::toKolonTanimi)
                .toList();
        List<KolonGuncelleme> guncellenecekKolonlar = request.guncellenecekKolonlarVeyaBos().stream()
                .map(g -> new KolonGuncelleme(g.kolonId(), g.yeniIsim(), g.yeniTagId(), g.yeniPrimaryKey()))
                .toList();
        return TabloResponse.from(tabloService.applyChanges(id, request.yeniIsim(), request.yeniSchemaId(),
                request.silinecekKolonIdlerVeyaBos(), eklenecekKolonlar, guncellenecekKolonlar));
    }
}

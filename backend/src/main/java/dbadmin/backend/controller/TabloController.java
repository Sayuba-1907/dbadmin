package dbadmin.backend.controller;

import dbadmin.backend.dto.ChangeTabloSchemaRequest;
import dbadmin.backend.dto.ChangeTagRequest;
import dbadmin.backend.dto.CreateKolonRequest;
import dbadmin.backend.dto.CreateTabloRequest;
import dbadmin.backend.dto.ErrorResponse;
import dbadmin.backend.dto.KolonResponse;
import dbadmin.backend.dto.RenameRequest;
import dbadmin.backend.dto.TabloResponse;
import dbadmin.backend.service.KolonTanimi;
import dbadmin.backend.service.TabloService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
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
                        schema = @Schema(implementation = ErrorResponse.class)))
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
                    + "tablosunu (otomatik 'id' PRIMARY KEY kolonuyla birlikte) olusturur. En az bir kolon "
                    + "zorunlu. schemaId de zorunlu — tablonun kurulacagi schema acikca verilmeli. Kolonlardan biri "
                    + "veya birden fazlasi primaryKey=true isaretlenirse, o kolon(lar) uzerine gercek bir "
                    + "Postgres UNIQUE constraint de kurulur.")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Tablo olusturuldu."),
        @ApiResponse(responseCode = "400",
                description = "Gecersiz tablo/kolon adi, gecersiz kolon tipi, hic kolon verilmemis, "
                        + "ya da schemaId bos birakilmis.",
                content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                        schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "404", description = "Belirtilen schemaId'de bir schema yok "
                + "(gizli 'public' schema'sinin id'si de buraya girer).",
                content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                        schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "409",
                description = "Bu isimde bir tablo zaten var, ya da request'te ayni isimde iki kolon var.",
                content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                        schema = @Schema(implementation = ErrorResponse.class)))
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
                        schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "404", description = "Bu id'de bir tablo yok.",
                content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                        schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "409", description = "Bu isimde baska bir tablo zaten var.",
                content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                        schema = @Schema(implementation = ErrorResponse.class)))
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
                        schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "404", description = "Tablo ya da hedef schema bulunamadi.",
                content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                        schema = @Schema(implementation = ErrorResponse.class)))
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
                        schema = @Schema(implementation = ErrorResponse.class)))
    })
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@Parameter(description = "Silinecek tablonun id'si.", example = "1") @PathVariable Long id) {
        tabloService.deleteTablo(id);
    }

    /** POST /api/tablolar/{id}/kolonlar — var olan tabloya yeni kolon ekler. */
    @Operation(summary = "Tabloya yeni kolon ekle",
            description = "Var olan bir tabloya hem metadata satiri hem gercek Postgres kolonu "
                    + "(ALTER TABLE ... ADD COLUMN) ekler. primaryKey=true gonderilirse, tablonun tum "
                    + "PK-isaretli kolonlarini kapsayan UNIQUE constraint bu yeni kolonu da icerecek "
                    + "sekilde yeniden kurulur.")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Kolon eklendi."),
        @ApiResponse(responseCode = "400", description = "Gecersiz kolon adi ya da gecersiz kolon tipi.",
                content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                        schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "404", description = "Tablo bulunamadi.",
                content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                        schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "409", description = "Bu tabloda ayni isimde bir kolon zaten var.",
                content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                        schema = @Schema(implementation = ErrorResponse.class)))
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
                    + "COLUMN) siler. Kolon PK-isaretliyse, ilgili UNIQUE constraint de otomatik "
                    + "guncellenir/kaldirilir. Kolonun bagli oldugu Tag silinmez.")
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Kolon silindi."),
        @ApiResponse(responseCode = "404", description = "Tablo ya da bu tabloda boyle bir kolon yok.",
                content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                        schema = @Schema(implementation = ErrorResponse.class)))
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
                        schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "404", description = "Tablo ya da bu tabloda boyle bir kolon yok.",
                content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                        schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "409", description = "Bu tabloda ayni isimde baska bir kolon zaten var.",
                content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                        schema = @Schema(implementation = ErrorResponse.class)))
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
                        schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PatchMapping("/{id}/kolonlar/{kolonId}/tag")
    public KolonResponse changeKolonTag(
            @Parameter(description = "Tablonun id'si.", example = "1") @PathVariable Long id,
            @Parameter(description = "Etiketi degistirilecek kolonun id'si.", example = "5") @PathVariable
                    Long kolonId,
            @RequestBody ChangeTagRequest request) {
        return KolonResponse.from(tabloService.changeKolonTag(id, kolonId, request.tagId()));
    }
}

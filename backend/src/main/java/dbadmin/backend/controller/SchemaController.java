package dbadmin.backend.controller;

import dbadmin.backend.dto.*;
import dbadmin.backend.service.SchemaService;
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

/** Schema icin HTTP endpoint'leri — {@link TabloController} ile ayni mantik, bkz. oradaki aciklama. */
@RestController
@RequestMapping("/api/schemalar")
@Tag(name = "Schemalar", description = "Postgres schema'larini (tablo gruplarini) yonetir; her yazma "
        + "islemi ayni anda gercek Postgres schema'sini da (CREATE/ALTER/DROP SCHEMA) degistirir. "
        + "'public' schema'si bu API'de hic gorunmez: listelenmez, id'siyle sorulursa 404 doner, bu "
        + "isimle yeni schema olusturulamaz ve oraya tablo kurulamaz/tasinamaz — altyapiya ait "
        + "(uygulamanin kendi metadata tablolari ve extension'lar orada durur).")
public class SchemaController {

    private final SchemaService schemaService;
    private final TabloService tabloService;

    public SchemaController(SchemaService schemaService, TabloService tabloService) {
        this.schemaService = schemaService;
        this.tabloService = tabloService;
    }

    @Operation(summary = "Tum schema'lari listele", description = "Kullanicinin olusturdugu tum "
            + "schema'lari doner. Altyapiya ait 'public' schema'si listeye dahil edilmez.")
    @ApiResponse(responseCode = "200", description = "Schema listesi (hicbir schema olusturulmamissa bos).")
    @GetMapping
    public List<SchemaResponse> list() {
        return schemaService.listSchemalar();
    }

    @GetMapping("/schemaList")
    public List<SchemaResponseDTO> getSchemaList() {
        List<SchemaResponseDTO> schemaList = schemaService.getSchemaList();
        return schemaList;
    }

    @Operation(summary = "Tek bir schema'yi getir", description = "Id'si verilen schema'nin bilgilerini doner.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Schema bulundu."),
        @ApiResponse(responseCode = "404", description = "Bu id'de bir schema yok.",
                content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                        schema = @Schema(implementation = ErrorResponse.class),
                        examples = @ExampleObject(name = "NOT_FOUND_SCHEMA",
                                summary = "Schema bulunamadi",
                                value = ErrorExamples.NOT_FOUND_SCHEMA)))
    })
    @GetMapping("/{id}")
    public SchemaResponse get(@Parameter(description = "Schema'nin id'si.", example = "1") @PathVariable Long id) {
        return schemaService.toResponse(schemaService.getSchema(id));
    }

    /** GET /api/schemalar/{id}/tablolar — sidebar'daki schema -> tablo hiyerarsisi icin, bir schema'nin altindaki tablolar. Schema yoksa 404. */
    @Operation(summary = "Bir schema'nin altindaki tablolari listele",
            description = "Sadece verilen schema'ya ait tablolari (kolonlariyla birlikte) doner. "
                    + "Frontend'deki sidebar'daki schema -> tablo hiyerarsisi icin kullanilir.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "O schema'nin tablo listesi (bos olabilir)."),
        @ApiResponse(responseCode = "404", description = "Bu id'de bir schema yok.",
                content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                        schema = @Schema(implementation = ErrorResponse.class),
                        examples = @ExampleObject(name = "NOT_FOUND_SCHEMA",
                                summary = "Schema bulunamadi",
                                value = ErrorExamples.NOT_FOUND_SCHEMA)))
    })
    @GetMapping("/{id}/tablolar")
    public List<TabloSummaryResponse> listTablolar(
            @Parameter(description = "Schema'nin id'si.", example = "1") @PathVariable Long id) {
        schemaService.getSchema(id);
        return tabloService.listTablolarBySchema(id).stream()
                .map(TabloSummaryResponse::from)
                .toList();
    }

    @Operation(summary = "Yeni schema olustur",
            description = "Hem metadata satirini hem de gercek Postgres schema'sini (CREATE SCHEMA) "
                    + "olusturur. 'public' ismiyle olusturulamaz (altyapiya ait rezerve isim).")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Schema olusturuldu."),
        @ApiResponse(responseCode = "400",
                description = "Gecersiz schema adi, ya da 'public' rezerve ismi kullanilmaya calisildi.",
                content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                        schema = @Schema(implementation = ErrorResponse.class),
                        examples = {
                            @ExampleObject(name = "VALIDATION_INVALID_SCHEMA_NAME",
                                    summary = "Schema adi kurallara uymuyor",
                                    value = ErrorExamples.VALIDATION_INVALID_SCHEMA_NAME),
                            @ExampleObject(name = "VALIDATION_RESERVED_SCHEMA_NAME",
                                    summary = "'public' rezerve isim",
                                    value = ErrorExamples.VALIDATION_RESERVED_SCHEMA_NAME)
                        })),
        @ApiResponse(responseCode = "409", description = "Bu isimde bir schema zaten var.",
                content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                        schema = @Schema(implementation = ErrorResponse.class),
                        examples = @ExampleObject(name = "CONFLICT_DUPLICATE_SCHEMA_NAME",
                                summary = "Ayni isimde schema var",
                                value = ErrorExamples.CONFLICT_DUPLICATE_SCHEMA_NAME)))
    })
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public SchemaResponse create(@RequestBody CreateSchemaRequest request) {
        return schemaService.toResponse(schemaService.createSchema(request.name()));
    }

    /** PATCH /api/schemalar/{id} — sadece ismi degistirir; yeni isim olarak "public" reddedilir (bkz. SchemaService.renameSchema). */
    @Operation(summary = "Schema'yi yeniden adlandir",
            description = "Hem metadata'daki hem gercek Postgres schema'sinin adini degistirir "
                    + "(ALTER SCHEMA ... RENAME TO). Hicbir schema 'public' ismine yeniden "
                    + "adlandirilamaz.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Schema yeniden adlandirildi."),
        @ApiResponse(responseCode = "400", description = "Gecersiz yeni isim, ya da yeni isim "
                + "olarak 'public' verilmeye calisildi.",
                content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                        schema = @Schema(implementation = ErrorResponse.class),
                        examples = {
                            @ExampleObject(name = "VALIDATION_INVALID_SCHEMA_NAME",
                                    summary = "Schema adi kurallara uymuyor",
                                    value = ErrorExamples.VALIDATION_INVALID_SCHEMA_NAME),
                            @ExampleObject(name = "VALIDATION_RESERVED_SCHEMA_NAME",
                                    summary = "'public' rezerve isim",
                                    value = ErrorExamples.VALIDATION_RESERVED_SCHEMA_NAME)
                        })),
        @ApiResponse(responseCode = "404", description = "Bu id'de bir schema yok.",
                content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                        schema = @Schema(implementation = ErrorResponse.class),
                        examples = @ExampleObject(name = "NOT_FOUND_SCHEMA",
                                summary = "Schema bulunamadi",
                                value = ErrorExamples.NOT_FOUND_SCHEMA))),
        @ApiResponse(responseCode = "409", description = "Bu isimde baska bir schema zaten var.",
                content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                        schema = @Schema(implementation = ErrorResponse.class),
                        examples = @ExampleObject(name = "CONFLICT_DUPLICATE_SCHEMA_NAME",
                                summary = "Ayni isimde schema var",
                                value = ErrorExamples.CONFLICT_DUPLICATE_SCHEMA_NAME)))
    })
    @PatchMapping("/{id}")
    public SchemaResponse rename(
            @Parameter(description = "Yeniden adlandirilacak schema'nin id'si.", example = "2") @PathVariable
                    Long id,
            @RequestBody RenameRequest request) {
        return schemaService.toResponse(schemaService.renameSchema(id, request.name()));
    }

    @Operation(summary = "Schema'yi sil",
            description = "DIKKAT: Yikici bir islem. Schema'nin altindaki TUM tablolarin metadata'sini "
                    + "siler, ardindan gercek Postgres schema'sini CASCADE ile siler — bu da icindeki "
                    + "tum tablolari fiziksel olarak siler.")
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Schema ve icindeki tum tablolar silindi."),
        @ApiResponse(responseCode = "404", description = "Bu id'de bir schema yok ('public'in id'si de "
                + "buraya girer — gizli oldugu icin yokmus gibi davranilir).",
                content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                        schema = @Schema(implementation = ErrorResponse.class),
                        examples = @ExampleObject(name = "NOT_FOUND_SCHEMA",
                                summary = "Schema bulunamadi",
                                value = ErrorExamples.NOT_FOUND_SCHEMA)))
    })
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@Parameter(description = "Silinecek schema'nin id'si.", example = "2") @PathVariable Long id) {
        schemaService.deleteSchema(id);
    }
}

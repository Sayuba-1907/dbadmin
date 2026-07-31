package dbadmin.backend.controller;

import dbadmin.backend.dto.CreateTagRequest;
import dbadmin.backend.dto.ErrorExamples;
import dbadmin.backend.dto.ErrorResponse;
import dbadmin.backend.dto.KolonUsageResponse;
import dbadmin.backend.dto.RenameRequest;
import dbadmin.backend.dto.TagResponse;
import dbadmin.backend.service.TagService;
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

/** Tag endpoint'leri — TabloController'a benzer ince bir katman, is mantigi {@link TagService}'te. */
@RestController
@RequestMapping("/api/tags")
@Tag(name = "Tagler", description = "Kolonlara baglanabilen etiketleri yonetir. Tag'lerin gercek Postgres "
        + "semasinda hicbir karsiligi yoktur (sadece metadata) — bu yuzden bu uclarda hicbir DDL "
        + "calismaz, sadece DB'deki Tag tablosu degisir.")
public class TagController {

    private final TagService tagService;

    public TagController(TagService tagService) {
        this.tagService = tagService;
    }

    /** GET /api/tags — tum etiketlerin listesi. */
    @Operation(summary = "Tum etiketleri listele", description = "Sistemdeki tum etiketleri doner.")
    @ApiResponse(responseCode = "200", description = "Etiket listesi (bos olabilir).")
    @GetMapping
    public List<TagResponse> list() {
        return tagService.listTags().stream().map(TagResponse::from).toList();
    }

    /** POST /api/tags — yeni etiket olusturur, 201 Created doner. */
    @Operation(summary = "Yeni etiket olustur", description = "Verilen isimle yeni bir etiket olusturur. "
            + "Bu etiket daha sonra kolonlara (bkz. PATCH /api/tablolar/{id}/kolonlar/{kolonId}/tag) "
            + "baglanabilir.")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Etiket olusturuldu."),
        @ApiResponse(responseCode = "400", description = "Gecersiz etiket adi.",
                content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                        schema = @Schema(implementation = ErrorResponse.class),
                        examples = @ExampleObject(name = "VALIDATION_INVALID_TAG_NAME",
                                summary = "Etiket adi kurallara uymuyor",
                                value = ErrorExamples.VALIDATION_INVALID_TAG_NAME))),
        @ApiResponse(responseCode = "409", description = "Bu isimde bir etiket zaten var.",
                content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                        schema = @Schema(implementation = ErrorResponse.class),
                        examples = @ExampleObject(name = "CONFLICT_DUPLICATE_TAG_NAME",
                                summary = "Ayni isimde etiket var",
                                value = ErrorExamples.CONFLICT_DUPLICATE_TAG_NAME)))
    })
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TagResponse create(@RequestBody CreateTagRequest request) {
        return TagResponse.from(tagService.createTag(request.name()));
    }

    /** GET /api/tags/{id}/kolonlar — bu tag'i tasiyan tum kolonlari, tablo/schema bilgisiyle birlikte doner. */
    @Operation(summary = "Bir etiketin kullanildigi kolonlari listele",
            description = "Verilen etiketi tasiyan tum kolonlari, hangi tabloda ve hangi schema'da "
                    + "olduklariyla birlikte doner. Sol menudeki 'Tagler' gorunumunde bir etiketin "
                    + "ayrinti butonuna basildiginda 'bu etiket hangi tablonun hangi kolonunda "
                    + "kullaniliyor' sorusunu cevaplamak icin kullanilir.")
    @ApiResponses({
        @ApiResponse(responseCode = "200",
                description = "Kullanildigi kolonlarin listesi (hic kolonda kullanilmiyorsa bos)."),
        @ApiResponse(responseCode = "404", description = "Bu id'de bir etiket yok.",
                content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                        schema = @Schema(implementation = ErrorResponse.class),
                        examples = @ExampleObject(name = "NOT_FOUND_TAG",
                                summary = "Etiket bulunamadi",
                                value = ErrorExamples.NOT_FOUND_TAG)))
    })
    @GetMapping("/{id}/kolonlar")
    public List<KolonUsageResponse> listUsage(
            @Parameter(description = "Etiketin id'si.", example = "1") @PathVariable Long id) {
        return tagService.getTagUsage(id).stream().map(KolonUsageResponse::from).toList();
    }

    /** PATCH /api/tags/{id} — sadece ismi degistirir (bkz. TagService.renameTag). */
    @Operation(summary = "Etiketi yeniden adlandir", description = "Verilen etiketin adini degistirir. "
            + "Etiketin gercek Postgres semasinda karsiligi olmadigi icin hicbir DDL calismaz.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Etiket yeniden adlandirildi."),
        @ApiResponse(responseCode = "400", description = "Gecersiz etiket adi.",
                content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                        schema = @Schema(implementation = ErrorResponse.class),
                        examples = @ExampleObject(name = "VALIDATION_INVALID_TAG_NAME",
                                summary = "Etiket adi kurallara uymuyor",
                                value = ErrorExamples.VALIDATION_INVALID_TAG_NAME))),
        @ApiResponse(responseCode = "404", description = "Bu id'de bir etiket yok.",
                content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                        schema = @Schema(implementation = ErrorResponse.class),
                        examples = @ExampleObject(name = "NOT_FOUND_TAG",
                                summary = "Etiket bulunamadi",
                                value = ErrorExamples.NOT_FOUND_TAG))),
        @ApiResponse(responseCode = "409", description = "Bu isimde baska bir etiket zaten var.",
                content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                        schema = @Schema(implementation = ErrorResponse.class),
                        examples = @ExampleObject(name = "CONFLICT_DUPLICATE_TAG_NAME",
                                summary = "Ayni isimde etiket var",
                                value = ErrorExamples.CONFLICT_DUPLICATE_TAG_NAME)))
    })
    @PatchMapping("/{id}")
    public TagResponse rename(
            @Parameter(description = "Yeniden adlandirilacak etiketin id'si.", example = "1") @PathVariable
                    Long id,
            @RequestBody RenameRequest request) {
        return TagResponse.from(tagService.renameTag(id, request.name()));
    }

    /** DELETE /api/tags/{id} — bkz. TagService.deleteTag (kullanan kolonlar etiketsiz kalir, silinmez). */
    @Operation(summary = "Etiketi sil", description = "Etiketi siler. Bu etiketi tasiyan kolonlar "
            + "silinmez — sadece etiket referanslari kaldirilir (kolonlar etiketsiz kalir).")
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Etiket silindi."),
        @ApiResponse(responseCode = "404", description = "Bu id'de bir etiket yok.",
                content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                        schema = @Schema(implementation = ErrorResponse.class),
                        examples = @ExampleObject(name = "NOT_FOUND_TAG",
                                summary = "Etiket bulunamadi",
                                value = ErrorExamples.NOT_FOUND_TAG)))
    })
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@Parameter(description = "Silinecek etiketin id'si.", example = "1") @PathVariable Long id) {
        tagService.deleteTag(id);
    }
}

package dbadmin.backend.controller;

import dbadmin.backend.dto.CreateTagRequest;
import dbadmin.backend.dto.ErrorResponse;
import dbadmin.backend.dto.TagResponse;
import dbadmin.backend.service.TagService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
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
                        schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "409", description = "Bu isimde bir etiket zaten var.",
                content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                        schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TagResponse create(@RequestBody CreateTagRequest request) {
        return TagResponse.from(tagService.createTag(request.name()));
    }
}

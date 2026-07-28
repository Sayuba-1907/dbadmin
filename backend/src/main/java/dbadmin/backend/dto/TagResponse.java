package dbadmin.backend.dto;

import dbadmin.backend.entity.Tag;
import io.swagger.v3.oas.annotations.media.Schema;

/** API'nin disari verdigi Tag govdesi (entity yerine DTO). */
public record TagResponse(
        @Schema(description = "Etiketin id'si.", example = "1") Long id,
        @Schema(description = "Etiketin adi.", example = "onemli") String name) {

    public static TagResponse from(Tag tag) {
        return new TagResponse(tag.getId(), tag.getName());
    }
}

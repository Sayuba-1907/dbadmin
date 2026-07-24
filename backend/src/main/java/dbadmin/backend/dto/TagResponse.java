package dbadmin.backend.dto;

import dbadmin.backend.entity.Tag;

/** API'nin disari verdigi Tag govdesi (entity yerine DTO). */
public record TagResponse(Long id, String name) {

    public static TagResponse from(Tag tag) {
        return new TagResponse(tag.getId(), tag.getName());
    }
}

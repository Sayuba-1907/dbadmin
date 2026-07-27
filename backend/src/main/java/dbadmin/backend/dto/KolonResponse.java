package dbadmin.backend.dto;

import dbadmin.backend.entity.Kolon;
import dbadmin.backend.entity.Tag;

/**
 * API'nin disari verdigi Kolon govdesi. Tag'i tum {@code Tag} nesnesi olarak degil
 * {@code tagId} + {@code tagName} olarak duz (flatten) veriyoruz — frontend'in ekstra
 * bir nested object'i parse etmesine gerek kalmiyor.
 */
public record KolonResponse(Long id, String name, String type, Long tagId, String tagName, boolean primaryKey) {

    public static KolonResponse from(Kolon kolon) {
        Tag tag = kolon.getTag();
        Long tagId = tag == null ? null : tag.getId();
        String tagName = tag == null ? null : tag.getName();
        return new KolonResponse(
                kolon.getId(), kolon.getName(), kolon.getType(), tagId, tagName, kolon.isPrimaryKey());
    }
}

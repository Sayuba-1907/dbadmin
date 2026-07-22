package dbadmin.backend.dto;

import dbadmin.backend.entity.Kolon;
import dbadmin.backend.entity.Tag;
//KolonResponse, veritabanındaki bir kolonu dış dünyaya (ön yüze) güvenli ve
//anlaşılır bir şekilde raporlamak için kullanılan sunum tabağıdır.
public record KolonResponse(Long id, String name, String type, Long tagId, String tagName) {

    public static KolonResponse from(Kolon kolon) {
        Tag tag = kolon.getTag();
        Long tagId = tag == null ? null : tag.getId();
        String tagName = tag == null ? null : tag.getName();
        return new KolonResponse(kolon.getId(), kolon.getName(), kolon.getType(), tagId, tagName);
    }
}

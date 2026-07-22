package dbadmin.backend.dto;

import dbadmin.backend.service.KolonTanimi;
//CreateKolonRequest, yeni bir kolon kurmak için dışarıdan gelen "adı ne, türü ne,
// etiketi ne?" bilgilerini taşıyan ve mutfağa uygun formata çeviren bir hazırlık kutusudur.
// Request-side shape for defining one column, either at table creation
// time or via the "add column" endpoint.
public record CreateKolonRequest(String name, String type, Long tagId) {

    public KolonTanimi toKolonTanimi() {
        return new KolonTanimi(name, type, tagId);
    }
}

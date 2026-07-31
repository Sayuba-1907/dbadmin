package dbadmin.backend.service;

/**
 * "Toplu degisiklikte var olan bir kolonu su nihai hale getir" icin kucuk veri tasiyici (record)
 * — {@link KolonTanimi} gibi DTO degildir, controller'dan service'e gecerken kullanilan ic tip.
 * {@link dbadmin.backend.dto.KolonGuncellemeRequest} ile ayni sekil, sadece paket sinirini asmiyor.
 */
public record KolonGuncelleme(Long kolonId, String yeniIsim, Long yeniTagId, boolean yeniPrimaryKey) {
}

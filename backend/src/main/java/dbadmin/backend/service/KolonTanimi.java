package dbadmin.backend.service;

/**
 * "Tablo olustururken bir kolon tanimla" icin kucuk veri tasiyici (record).
 * DTO degildir — DTO'lar API'nin disari verdigi sozlesme (dto paketinde), bu ise sadece
 * controller'dan service'e gecerken (String, String, Long) parametre listesi yerine
 * kullanilan ic (internal) bir tip.
 */
public record KolonTanimi(String name, String type, Long tagId) {
}

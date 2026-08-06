package dbadmin.backend.entity;

/**
 * {@link AuditLog}'un kapsadigi mutasyonlar (bkz. {@code requirement-audit-log.md} Req-2.1).
 * Salt-okunur (GET) uclar icin deger yok — bunlar hicbir zaman audit'lenmez.
 */
public enum IslemTipi {
    TABLO_OLUSTURULDU,
    TABLO_SILINDI,
    TABLO_YENIDEN_ADLANDIRILDI,
    TABLO_SEMASI_DEGISTIRILDI,
    /** "Kaydet"le tek seferde birden fazla degisiklik uygulandiginda (bkz. TabloService#applyChanges) tek bir ozet satiri. */
    TABLO_GUNCELLENDI,

    KOLON_EKLENDI,
    KOLON_SILINDI,
    KOLON_YENIDEN_ADLANDIRILDI,
    KOLON_PRIMARY_KEY_DEGISTIRILDI,
    KOLON_TAG_DEGISTIRILDI,

    SCHEMA_OLUSTURULDU,
    SCHEMA_YENIDEN_ADLANDIRILDI,
    SCHEMA_SILINDI,

    TAG_OLUSTURULDU,
    TAG_YENIDEN_ADLANDIRILDI,
    TAG_SILINDI,

    KULLANICI_OLUSTURULDU,
    KULLANICI_ROLU_DEGISTIRILDI,
    KULLANICI_SILINDI
}

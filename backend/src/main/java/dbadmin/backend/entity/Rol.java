package dbadmin.backend.entity;

/**
 * Bir kullanicinin yetki seviyesi. Uc seviye birikimlidir: her seviye bir ustunun
 * yapabildiklerini de yapabilir (bkz. {@link dbadmin.backend.security.SecurityConfig}).
 * <p>
 * Spring Security yetkileri {@code ROLE_} onekiyle bekler ({@code hasRole("EDITOR")}
 * aslinda {@code ROLE_EDITOR} otoritesini arar), ama veritabaninda oneksiz saklariz —
 * onek {@link #authority()} icinde eklenir. Boylece DB'deki deger Spring'in ic
 * detayindan bagimsiz kalir.
 */
public enum Rol {

    /** Sadece okuyabilir: butun GET uclari. Hicbir sey yaratamaz/degistiremez/silemez. */
    VIEWER,

    /** VIEWER'in her seyi + tablo/kolon/schema/tag yazma islemleri. Kullanici yonetemez. */
    EDITOR,

    /** EDITOR'un her seyi + kullanici yonetimi ({@code /api/kullanicilar}). */
    ADMIN;

    /** Spring Security'nin bekledigi {@code ROLE_}'lu otorite adi. */
    public String authority() {
        return "ROLE_" + name();
    }
}

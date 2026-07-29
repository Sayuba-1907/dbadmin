package dbadmin.backend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/**
 * Uygulamaya giris yapabilen bir kullanici. Diger metadata tablolari ({@code tablo},
 * {@code kolon}, {@code sema}, {@code tag}) gibi {@code public} semasinda yasar ve
 * API'den gorunmez — {@code public} zaten uygulamanin kendi ic tablolarinin yeridir
 * (bkz. SchemaService.isHidden).
 * <p>
 * Parola asla duz metin tutulmaz: {@link #parolaHash} BCrypt ciktisidir ve tek yonludur,
 * yani DB'yi ele geciren biri bile parolalari geri cozemez. Hash'leme
 * {@link dbadmin.backend.service.KullaniciService} icinde yapilir; bu sinif hash'i
 * oldugu gibi saklar, kendisi hash'lemez.
 */
@Entity
@Table(
        name = "kullanici",
        uniqueConstraints = @UniqueConstraint(columnNames = "kullanici_adi"))
public class Kullanici {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "kullanici_adi", nullable = false)
    private String kullaniciAdi;

    @Column(name = "parola_hash", nullable = false)
    private String parolaHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Rol rol;

    /** JPA/Hibernate'in reflection ile nesne olusturabilmesi icin zorunlu parametresiz constructor. */
    protected Kullanici() {
    }

    public Kullanici(String kullaniciAdi, String parolaHash, Rol rol) {
        this.kullaniciAdi = kullaniciAdi;
        this.parolaHash = parolaHash;
        this.rol = rol;
    }

    public Long getId() {
        return id;
    }

    public String getKullaniciAdi() {
        return kullaniciAdi;
    }

    public String getParolaHash() {
        return parolaHash;
    }

    public void setParolaHash(String parolaHash) {
        this.parolaHash = parolaHash;
    }

    public Rol getRol() {
        return rol;
    }

    public void setRol(Rol rol) {
        this.rol = rol;
    }

    /** Sadece id'ye gore esitlik: kaydedilmemis (id=null) nesneler asla birbirine esit sayilmaz. */
    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Kullanici other)) {
            return false;
        }
        return id != null && id.equals(other.id);
    }

    /** Sabit deger: id degisebildigi icin id'yi hashCode'a katmiyoruz. */
    @Override
    public int hashCode() {
        return getClass().hashCode();
    }

    /** Parola hash'i loglara/hata mesajlarina sizmasin diye bilerek disarida birakildi. */
    @Override
    public String toString() {
        return "Kullanici{id=" + id + ", kullaniciAdi='" + kullaniciAdi + "', rol=" + rol + "}";
    }
}

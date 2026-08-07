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
 * Uygulamaya giris yapabilen bir kullanici. Diger metadata tablolari ({@code tables},
 * {@code columns}, {@code schemas}, {@code tag}) gibi {@code public} semasinda yasar ve
 * API'den gorunmez — {@code public} zaten uygulamanin kendi ic tablolarinin yeridir
 * (bkz. SchemaService.isHidden).
 * <p>
 * Parola asla duz metin tutulmaz: {@link #passwordHash} BCrypt ciktisidir ve tek yonludur,
 * yani DB'yi ele geciren biri bile parolalari geri cozemez. Hash'leme
 * {@link dbadmin.backend.service.UserService} icinde yapilir; bu sinif hash'i
 * oldugu gibi saklar, kendisi hash'lemez.
 */
@Entity
@Table(
        name = "users",
        uniqueConstraints = @UniqueConstraint(columnNames = "username"))
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "username", nullable = false)
    private String username;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role;

    /** JPA/Hibernate'in reflection ile nesne olusturabilmesi icin zorunlu parametresiz constructor. */
    protected User() {
    }

    public User(String username, String passwordHash, Role role) {
        this.username = username;
        this.passwordHash = passwordHash;
        this.role = role;
    }

    public Long getId() {
        return id;
    }

    public String getUsername() {
        return username;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public Role getRole() {
        return role;
    }

    public void setRole(Role role) {
        this.role = role;
    }

    /** Sadece id'ye gore esitlik: kaydedilmemis (id=null) nesneler asla birbirine esit sayilmaz. */
    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof User other)) {
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
        return "User{id=" + id + ", username='" + username + "', role=" + role + "}";
    }
}

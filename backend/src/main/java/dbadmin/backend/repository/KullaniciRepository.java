package dbadmin.backend.repository;

import dbadmin.backend.entity.Kullanici;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/** Spring Data JPA repository — method-name'den otomatik sorgu uretilir, implementasyon yazilmaz. */
public interface KullaniciRepository extends JpaRepository<Kullanici, Long> {

    /** Girişte ve her JWT dogrulamasinda kullanilir — kullanici adi login'in dogal anahtaridir. */
    Optional<Kullanici> findByKullaniciAdi(String kullaniciAdi);

    /** Tam satiri cekmeden sadece var/yok bilgisini doner — uniqueness kontrolu icin daha ucuz. */
    boolean existsByKullaniciAdi(String kullaniciAdi);
}

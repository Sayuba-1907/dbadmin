package dbadmin.backend.repository;

import dbadmin.backend.entity.Tablo;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Spring Data JPA repository: burada implementasyon yazmiyoruz, Spring metod ismini
 * okuyup ("findByName" -> {@code WHERE name = ?}) SQL sorgusunu otomatik uretiyor
 * (method-name query derivation). {@link JpaRepository} zaten save/findById/findAll/delete
 * gibi temel CRUD'u hazir getirir.
 */
public interface TabloRepository extends JpaRepository<Tablo, Long> {

    Optional<Tablo> findByName(String name);

    /** Tam satiri cekmeden sadece var/yok bilgisini doner — uniqueness kontrolu icin findByName'den daha ucuz. */
    boolean existsByName(String name);
}

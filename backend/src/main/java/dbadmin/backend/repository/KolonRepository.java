package dbadmin.backend.repository;

import dbadmin.backend.entity.Kolon;
import dbadmin.backend.entity.Tablo;
import org.springframework.data.jpa.repository.JpaRepository;

/** Spring Data JPA repository — method-name'den otomatik sorgu uretilir, implementasyon yazilmaz. */
public interface KolonRepository extends JpaRepository<Kolon, Long> {

    /** Ayni tabloda ayni isimde baska kolon var mi (uniqueConstraint = {tablo_id, name} kuralinin kontrolu). */
    boolean existsByTabloAndName(Tablo tablo, String name);
}
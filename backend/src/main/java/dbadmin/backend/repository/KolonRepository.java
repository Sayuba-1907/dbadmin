package dbadmin.backend.repository;

import dbadmin.backend.entity.Kolon;
import dbadmin.backend.entity.Tablo;
import java.util.List;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

/** Spring Data JPA repository — method-name'den otomatik sorgu uretilir, implementasyon yazilmaz. */
public interface KolonRepository extends JpaRepository<Kolon, Long> {

    /** Ayni tabloda ayni isimde baska kolon var mi (uniqueConstraint = {tablo_id, name} kuralinin kontrolu). */
    boolean existsByTabloAndName(Tablo tablo, String name);

    /**
     * "Bu tag hangi tablo/kolonlarda kullaniliyor" sorgusu icin (bkz. TagService.getTagUsage).
     * {@code tablo} ve {@code tablo.schema} LAZY oldugu icin {@code @EntityGraph} olmadan her
     * kolon icin ayri sorgu atilirdi (N+1) — TabloRepository'deki ayni desen, bkz. oradaki
     * aciklama. Ikisi de tekil (ManyToOne) iliski oldugu icin MultipleBagFetchException riski yok.
     */
    @EntityGraph(attributePaths = {"tablo", "tablo.schema"})
    List<Kolon> findByTagId(Long tagId);
}
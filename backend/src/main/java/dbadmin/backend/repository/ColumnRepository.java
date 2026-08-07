package dbadmin.backend.repository;

import dbadmin.backend.entity.DataColumn;
import dbadmin.backend.entity.DataTable;
import java.util.List;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

/** Spring Data JPA repository — method-name'den otomatik sorgu uretilir, implementasyon yazilmaz. */
public interface ColumnRepository extends JpaRepository<DataColumn, Long> {

    /** Ayni tabloda ayni isimde baska kolon var mi (uniqueConstraint = {table_id, name} kuralinin kontrolu). */
    boolean existsByTableAndName(DataTable table, String name);

    /**
     * "Bu tag hangi tablo/kolonlarda kullaniliyor" sorgusu icin (bkz. TagService.getTagUsage).
     * {@code table} ve {@code table.schema} LAZY oldugu icin {@code @EntityGraph} olmadan her
     * kolon icin ayri sorgu atilirdi (N+1) — TableRepository'deki ayni desen, bkz. oradaki
     * aciklama. Ikisi de tekil (ManyToOne) iliski oldugu icin MultipleBagFetchException riski yok.
     */
    @EntityGraph(attributePaths = {"table", "table.schema"})
    List<DataColumn> findByTagId(Long tagId);
}

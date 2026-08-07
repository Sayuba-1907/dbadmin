package dbadmin.backend.repository;

import dbadmin.backend.entity.Schema;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** {@link TableRepository} ile ayni mantik — bkz. oradaki aciklama. */
public interface SchemaRepository extends JpaRepository<Schema, Long> {

    boolean existsByName(String name);

    /** Testlerin "public" satiri gercekten yok mu" diye kontrol edebilmesi icin. */
    Optional<Schema> findByNameIgnoreCase(String name);

    /**
     * {@code GET /api/schemas}'in sayfalanmis hali icin. {@link TableRepository#findPageableIdsExcludingSchema}'daki
     * iki-adimli id-sonra-fetch numarasina burada GEREK YOK: {@code Schema} entity'sinde
     * (Table'daki {@code columns} gibi) sayfalamayi bozacak bir to-many {@code @EntityGraph}
     * ilişkisi yok, o yuzden tek bir dogrudan sorgu yeterli. Filtre yine de var — "public" adinda
     * bir schema satiri normalde HIC olusturulmaz (bkz. CLAUDE.md), ama bu WHERE olmadan sayfalama
     * altinda (varsayimsal olarak bir gun boyle bir satir olursa) Page'in totalElements'i, servis
     * katmaninda post-fetch filtrelemenin bozacagi yanlis bir sayi verirdi.
     */
    @Query("select s from Schema s where upper(s.name) <> upper(:hiddenSchemaName)")
    Page<Schema> findAllExcludingSchema(@Param("hiddenSchemaName") String hiddenSchemaName, Pageable pageable);
}

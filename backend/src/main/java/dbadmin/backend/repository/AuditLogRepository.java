package dbadmin.backend.repository;

import dbadmin.backend.entity.AuditLog;
import dbadmin.backend.entity.HedefTip;
import java.time.Instant;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Spring Data JPA repository — {@code GET /api/audit-loglar}'in (Req-2.4) filtreleri hepsi
 * opsiyonel ve birlikte kullanilabilir olmali (ör. "sadece kullaniciId" ya da "kullaniciId +
 * tarih araligi"); ayri ayri method-name sorgulari (findByX, findByXAndY, ...) bu kombinasyonlari
 * kapsayamayacagi icin tek, parametreleri {@code IS NULL} ile atlayan bir JPQL sorgusu kullanildi.
 */
public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    // "CAST(:param AS ...) IS NULL" -- Postgres, JDBC extended query protokolunde tek basina
    // "? IS NULL" seklinde kullanilan bir parametrenin tipini cikaramiyor ("could not determine
    // data type of parameter"); acik cast bu belirsizligi kaldiriyor.
    @Query("""
            SELECT a FROM AuditLog a
            WHERE (:kullaniciId IS NULL OR a.kullaniciId = :kullaniciId)
              AND (CAST(:hedefTip AS string) IS NULL OR a.hedefTip = :hedefTip)
              AND (CAST(:bas AS timestamp) IS NULL OR a.olusturulmaZamani >= :bas)
              AND (CAST(:bit AS timestamp) IS NULL OR a.olusturulmaZamani <= :bit)
            """)
    Page<AuditLog> ara(
            @Param("kullaniciId") Long kullaniciId,
            @Param("hedefTip") HedefTip hedefTip,
            @Param("bas") Instant bas,
            @Param("bit") Instant bit,
            Pageable pageable);
}

package dbadmin.backend.repository;

import dbadmin.backend.entity.AuditLog;
import dbadmin.backend.entity.TargetType;
import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

/**
 * Spring Data JPA repository — {@code GET /api/audit-logs}'in (Req-2.4) filtreleri hepsi
 * opsiyonel ve birlikte kullanilabilir olmali (ör. "sadece userId" ya da "userId +
 * tarih araligi"); ayri ayri method-name sorgulari (findByX, findByXAndY, ...) bu kombinasyonlari
 * kapsayamayacagi icin tek, parametreleri {@code IS NULL} ile atlayan bir JPQL sorgusu kullanildi.
 */
public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    // "CAST(:param AS ...) IS NULL" -- Postgres, JDBC extended query protokolunde tek basina
    // "? IS NULL" seklinde kullanilan bir parametrenin tipini cikaramiyor ("could not determine
    // data type of parameter"); acik cast bu belirsizligi kaldiriyor.
    @Query("""
            SELECT a FROM AuditLog a
            WHERE (:userId IS NULL OR a.userId = :userId)
              AND (CAST(:targetType AS string) IS NULL OR a.targetType = :targetType)
              AND (:targetId IS NULL OR a.targetId = :targetId)
              AND (CAST(:from AS timestamp) IS NULL OR a.createdAt >= :from)
              AND (CAST(:to AS timestamp) IS NULL OR a.createdAt <= :to)
            """)
    Page<AuditLog> search(
            @Param("userId") Long userId,
            @Param("targetType") TargetType targetType,
            @Param("targetId") Long targetId,
            @Param("from") Instant from,
            @Param("to") Instant to,
            Pageable pageable);

    /**
     * Yedekleme icin (bkz. requirement-maintenance-audit-backup.md Req-2.4.1) tum tabloyu id
     * sirasiyla okur — son elemanin id'si bir sonraki adimdaki (silme) cutoff'u belirler.
     */
    List<AuditLog> findAllByOrderByIdAsc();

    /**
     * Yedekleme sonrasi temizlik (Req-2.5): blanket {@code DELETE FROM audit_log} DEGIL, sadece
     * okuma anindaki en yuksek id'ye kadar. Okuma (yukaridaki metod) ile bu silme arasinda yeni
     * bir satir yazilmissa (id > cutoffId), bu satir silinmez — bir sonraki yedeklemede o da
     * dahil edilir.
     * <p>
     * {@code @Transactional} burada, {@code @Modifying} sorgularin (base CRUD metodlarindan
     * farkli olarak) kendiliginden bir transaction acmamasi yuzunden gerekli — cagiran zaten
     * {@code @Transactional} olsa da (bkz. AuditLogBackupService#backup), bu metod boylece tek
     * basina da (ör. testte) guvenle cagrilabilir.
     */
    @Modifying
    @Transactional
    long deleteByIdLessThanEqual(Long cutoffId);
}

package dbadmin.backend.repository;

import dbadmin.backend.entity.DataTable;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Spring Data JPA repository: burada implementasyon yazmiyoruz, Spring metod ismini
 * okuyup ("findByName" -> {@code WHERE name = ?}) SQL sorgusunu otomatik uretiyor
 * (method-name query derivation). {@link JpaRepository} zaten save/findById/findAll/delete
 * gibi temel CRUD'u hazir getirir.
 */
public interface TableRepository extends JpaRepository<DataTable, Long> {
    long countBySchemaId(Long schemaId);
    /**
     * {@code TableResponse.from()} her tablo icin kolonlarina, her kolonun tag'ine ve tablonun
     * schema'sina dokunuyor. Bu iliskiler LAZY oldugu icin, listeyi cektikten sonra her tabloya
     * erisildiginde Hibernate ayri birer sorgu atardi: 1 (liste) + N (kolonlar) + ... = N+1 problemi.
     * Olculdu: 11 tablo icin tek bir liste istegi 21 sorgu uretiyordu.
     *
     * <p>{@code @EntityGraph} bu iliskileri ayni sorguya LEFT JOIN olarak ekletir, sonuc tek sorgu olur.
     * Ayni tablo satiri her kolonu icin tekrar dondugu icin JOIN sonucunda cogalir; Hibernate 6+
     * kok entity'leri otomatik tekillestirdigi icin ayrica {@code distinct} yazmaya gerek yok.
     *
     * <p>Not: {@code columns} tek koleksiyon oldugu icin sorun yok — ikinci bir List iliskisi daha
     * fetch edilseydi Hibernate {@code MultipleBagFetchException} atardi.
     */
    @EntityGraph(attributePaths = {"columns", "columns.tag", "schema"})
    Optional<DataTable> findByName(String name);

    /**
     * JpaRepository'den gelen findById'i sadece {@code @EntityGraph} eklemek icin ezyoruz —
     * imza ayni, davranis ayni, tek fark iliskilerin ayri sorgularla degil ayni sorguda gelmesi.
     * Tekil uc noktalarda (GET/PATCH/DELETE /api/tables/{id}) 4 sorguyu 1'e indirir.
     */
    @Override
    @EntityGraph(attributePaths = {"columns", "columns.tag", "schema"})
    Optional<DataTable> findById(Long id);

    /** Tum tablolari isme gore alfabetik sirali doner — findAll() sirasiz oldugu icin (DB'nin ne dondurdugune bagli). */
    @EntityGraph(attributePaths = {"columns", "columns.tag", "schema"})
    List<DataTable> findAllByOrderByNameAsc();

    /**
     * {@code GET /api/tables}'in sayfalanmis hali icin ADIM 1: sadece id'leri sayfalar, entity'nin
     * kendisini (ve iliskilerini) hic cekmez. Neden iki adimli: {@code @EntityGraph(columns, ...)}
     * ile {@code columns} (to-many) iliskisini ayni sorguya LEFT JOIN olarak eklersek, sonuc satir
     * sayisi kolon sayisi kadar cogalir — Spring Data bunun farkina varip Pageable'in LIMIT/OFFSET'ini
     * artik SQL'de degil, TUM satirlari cekip Java tarafinda ("HHH000104: firstResult/maxResults
     * specified with collection fetch; applying in memory" uyarisiyla) uygular. Yani Pageable eklemis
     * gibi görünürüz ama gercekte hala tum tablolari cekeriz — sayfalamanin butun amaci bosa gider.
     * Bu metod fetch join icermedigi icin LIMIT/OFFSET gercekten SQL'de calisir; tam entity'ler
     * {@link #findAllByIdInOrderByNameAsc} ile (kucuk, sabit boyutlu id listesiyle) ikinci ucuz bir
     * sorguda cekilir.
     */
    @Query("select t.id from DataTable t where upper(t.schema.name) <> upper(:hiddenSchemaName)")
    Page<Long> findPageableIdsExcludingSchema(@Param("hiddenSchemaName") String hiddenSchemaName, Pageable pageable);

    /** ADIM 2: ilk adimda sayfalanmis id'lerin tam halini (kolon/tag/schema dahil) TEK sorguda getirir. */
    @EntityGraph(attributePaths = {"columns", "columns.tag", "schema"})
    List<DataTable> findAllByIdInOrderByNameAsc(List<Long> ids);

    /** Tam satiri cekmeden sadece var/yok bilgisini doner — uniqueness kontrolu icin findByName'den daha ucuz. */
    boolean existsByName(String name);

    /** Bir schema'nin altindaki tablolari isme gore alfabetik siralar (sidebar'da schema -> tablo hiyerarsisi icin). */
    @EntityGraph(attributePaths = {"columns", "columns.tag", "schema"})
    List<DataTable> findBySchemaIdOrderByNameAsc(Long schemaId);

    /** TableOwnerBackfillRunner icin: sahibi henuz atanmamis (eski) tablolar. Kolon/schema fetch'ine gerek yok, sadece id yazilacak. */
    List<DataTable> findByCreatedByUserIdIsNull();

    /**
     * Workspace ekrani icin: TUM schema+tablo ciftlerini TEK sorguda ceker, kolonlara hic
     * dokunmadan. {@code findBySchemaIdOrderByNameAsc}'deki {@code @EntityGraph} kolonlari da
     * (ve tag'lerini) fetch ettigi icin sonuc satiri kolon sayisi kadar cogaliyordu (fan-out);
     * burada sadece schema/tablo id+name oldugu icin sonuc satir sayisi = tablo sayisi.
     *
     * <p>Projection interface (getX() metodlariyla), Hibernate sorguyu SELECT listesindeki
     * alanlarla sinirlar — DataTable/Schema entity'lerinin tamamini yuklemez.
     */
    @Query("""
            select t.schema.id as schemaId, t.schema.name as schemaName,
                   t.id as tableId, t.name as tableName
            from DataTable t
            order by t.schema.id, t.name
            """)
    List<SchemaTableProjection> findAllSchemaTablePairs();

    /**
     * Her tablonun kolon sayisini DB'de {@code COUNT} + {@code GROUP BY} ile saydirir — kolon
     * satirlarinin kendisini (isim, tag, primaryKey...) hic cekmeden. {@code TableSummaryResponse
     * .from()}'daki mevcut deseni (kolonlari tam fetch edip Java'da {@code .size()}) DB'ye
     * birakir: sonuc, kolon sayisi kadar tekrar eden satirlar degil, tablo basina TEK bir satir.
     *
     * <p>Not: hic kolonu olmayan bir tablo bu listede HIC gorunmez (COUNT'un GROUP BY'i, esleseni
     * olmayan satiri uretmez) — cagiran taraf, {@code findAllSchemaTablePairs}'ten gelen tablo
     * id'leriyle karsilastirip map'te karsiligi olmayanlari 0 kabul etmeli.
     */
    @Query("select c.table.id as tableId, count(c) as columnCount from DataColumn c group by c.table.id")
    List<TableColumnCountProjection> countColumnsGroupByTable();

    /** {@link #findAllSchemaTablePairs} icin projection — Spring Data, SELECT listesindeki alan adlarini bu getX() metodlariyla eslestirir. */
    interface SchemaTableProjection {
        Long getSchemaId();
        String getSchemaName();
        Long getTableId();
        String getTableName();
    }

    /** {@link #countColumnsGroupByTable} icin projection. */
    interface TableColumnCountProjection {
        Long getTableId();
        Long getColumnCount();
    }
}

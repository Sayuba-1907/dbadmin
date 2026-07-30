package dbadmin.backend.repository;

import dbadmin.backend.entity.Tablo;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

/**
 * Spring Data JPA repository: burada implementasyon yazmiyoruz, Spring metod ismini
 * okuyup ("findByName" -> {@code WHERE name = ?}) SQL sorgusunu otomatik uretiyor
 * (method-name query derivation). {@link JpaRepository} zaten save/findById/findAll/delete
 * gibi temel CRUD'u hazir getirir.
 */
public interface TabloRepository extends JpaRepository<Tablo, Long> {
    long countBySchemaId(Long schemaId);
    /**
     * {@code TabloResponse.from()} her tablo icin kolonlarina, her kolonun tag'ine ve tablonun
     * schema'sina dokunuyor. Bu iliskiler LAZY oldugu icin, listeyi cektikten sonra her tabloya
     * erisildiginde Hibernate ayri birer sorgu atardi: 1 (liste) + N (kolonlar) + ... = N+1 problemi.
     * Olculdu: 11 tablo icin tek bir liste istegi 21 sorgu uretiyordu.
     *
     * <p>{@code @EntityGraph} bu iliskileri ayni sorguya LEFT JOIN olarak ekletir, sonuc tek sorgu olur.
     * Ayni tablo satiri her kolonu icin tekrar dondugu icin JOIN sonucunda cogalir; Hibernate 6+
     * kok entity'leri otomatik tekillestirdigi icin ayrica {@code distinct} yazmaya gerek yok.
     *
     * <p>Not: {@code kolonlar} tek koleksiyon oldugu icin sorun yok — ikinci bir List iliskisi daha
     * fetch edilseydi Hibernate {@code MultipleBagFetchException} atardi.
     */
    @EntityGraph(attributePaths = {"kolonlar", "kolonlar.tag", "schema"})
    Optional<Tablo> findByName(String name);

    /**
     * JpaRepository'den gelen findById'i sadece {@code @EntityGraph} eklemek icin ezyoruz —
     * imza ayni, davranis ayni, tek fark iliskilerin ayri sorgularla degil ayni sorguda gelmesi.
     * Tekil uc noktalarda (GET/PATCH/DELETE /api/tablolar/{id}) 4 sorguyu 1'e indirir.
     */
    @Override
    @EntityGraph(attributePaths = {"kolonlar", "kolonlar.tag", "schema"})
    Optional<Tablo> findById(Long id);

    /** Tum tablolari isme gore alfabetik sirali doner — findAll() sirasiz oldugu icin (DB'nin ne dondurdugune bagli). */
    @EntityGraph(attributePaths = {"kolonlar", "kolonlar.tag", "schema"})
    List<Tablo> findAllByOrderByNameAsc();

    

    /** Tam satiri cekmeden sadece var/yok bilgisini doner — uniqueness kontrolu icin findByName'den daha ucuz. */
    boolean existsByName(String name);

    /** Bir schema'nin altindaki tablolari isme gore alfabetik siralar (sidebar'da schema -> tablo hiyerarsisi icin). */
    @EntityGraph(attributePaths = {"kolonlar", "kolonlar.tag", "schema"})
    List<Tablo> findBySchemaIdOrderByNameAsc(Long schemaId);

    /**
     * Workspace ekrani icin: TUM schema+tablo ciftlerini TEK sorguda ceker, kolonlara hic
     * dokunmadan. {@code findBySchemaIdOrderByNameAsc}'deki {@code @EntityGraph} kolonlari da
     * (ve tag'lerini) fetch ettigi icin sonuc satiri kolon sayisi kadar cogaliyordu (fan-out);
     * burada sadece schema/tablo id+name oldugu icin sonuc satir sayisi = tablo sayisi.
     *
     * <p>Projection interface (getX() metodlariyla), Hibernate sorguyu SELECT listesindeki
     * alanlarla sinirlar — Tablo/Schema entity'lerinin tamamini yuklemez.
     */
    @Query("""
            select t.schema.id as schemaId, t.schema.name as schemaName,
                   t.id as tabloId, t.name as tabloName
            from Tablo t
            order by t.schema.id, t.name
            """)
    List<SchemaTabloProjection> findAllSchemaTabloPairs();

    /**
     * Her tablonun kolon sayisini DB'de {@code COUNT} + {@code GROUP BY} ile saydirir — kolon
     * satirlarinin kendisini (isim, tag, primaryKey...) hic cekmeden. {@code TabloSummaryResponse
     * .from()}'daki mevcut deseni (kolonlari tam fetch edip Java'da {@code .size()}) DB'ye
     * birakir: sonuc, kolon sayisi kadar tekrar eden satirlar degil, tablo basina TEK bir satir.
     *
     * <p>Not: hic kolonu olmayan bir tablo bu listede HIC gorunmez (COUNT'un GROUP BY'i, esleseni
     * olmayan satiri uretmez) — cagiran taraf, {@code findAllSchemaTabloPairs}'ten gelen tablo
     * id'leriyle karsilastirip map'te karsiligi olmayanlari 0 kabul etmeli.
     */
    @Query("select k.tablo.id as tabloId, count(k) as kolonSayisi from Kolon k group by k.tablo.id")
    List<TabloKolonCountProjection> countKolonlarGroupByTablo();

    /**
     * {@link #countKolonlarGroupByTablo}'nun List sonucunu Map'e cevirir — Spring Data
     * {@code @Query} metotlari dogrudan {@code Map} donemez (Hibernate sonucu her zaman
     * List'tir), donusumu burada (sorgunun hemen yaninda) yapip cagiran tarafa (servis)
     * tasimiyoruz. Hic kolonu olmayan bir tablo bu Map'te YOK — cagiran taraf
     * {@code map.getOrDefault(tabloId, 0L)} ile 0 kabul etmeli.
     */

    /** {@link #findAllSchemaTabloPairs} icin projection — Spring Data, SELECT listesindeki alan adlarini bu getX() metodlariyla eslestirir. */
    interface SchemaTabloProjection {
        Long getSchemaId();
        String getSchemaName();
        Long getTabloId();
        String getTabloName();
    }

    /** {@link #countKolonlarGroupByTablo} icin projection. */
    interface TabloKolonCountProjection {
        Long getTabloId();
        Long getKolonSayisi();
    }
}

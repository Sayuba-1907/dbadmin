package dbadmin.backend.repository;

import dbadmin.backend.entity.Tablo;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

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
}

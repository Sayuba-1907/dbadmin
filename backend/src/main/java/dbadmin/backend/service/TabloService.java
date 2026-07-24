package dbadmin.backend.service;

import dbadmin.backend.ddl.ColumnType;
import dbadmin.backend.ddl.TableDdlExecutor;
import dbadmin.backend.entity.Kolon;
import dbadmin.backend.entity.Tablo;
import dbadmin.backend.entity.Tag;
import dbadmin.backend.exception.ConflictException;
import dbadmin.backend.exception.NotFoundException;
import dbadmin.backend.exception.ValidationException;
import dbadmin.backend.repository.KolonRepository;
import dbadmin.backend.repository.TabloRepository;
import dbadmin.backend.repository.TagRepository;
import dbadmin.backend.validation.NameValidator;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Is mantiginin (business logic) yasadigi yer — controller'lar burayi cagirir, burasi degil onlari.
 * Her public metod iki isi birden yapar: (1) metadata'yi ({@code Tablo}/{@code Kolon} satirlari)
 * gunceller, (2) {@link TableDdlExecutor} uzerinden gercek Postgres semasini ayni yonde degistirir.
 * Ikisi de ayni {@code @Transactional} icinde oldugu icin biri patlarsa oburu de geri alinir,
 * boylece metadata ile gercek DB semasi hicbir zaman birbirinden kopmaz.
 */
@Service
public class TabloService {

    private final TabloRepository tabloRepository;
    private final KolonRepository kolonRepository;
    private final TagRepository tagRepository;
    private final TableDdlExecutor ddlExecutor;

    public TabloService(TabloRepository tabloRepository, KolonRepository kolonRepository,
            TagRepository tagRepository, TableDdlExecutor ddlExecutor) {
        this.tabloRepository = tabloRepository;
        this.kolonRepository = kolonRepository;
        this.tagRepository = tagRepository;
        this.ddlExecutor = ddlExecutor;
    }
    /**
     * {@code @Transactional}: bu metod icindeki tum DB islemlerini tek bir islem paketine alir;
     * hepsi basarili olursa commit eder, biri hata verirse tumunu geri alir (rollback).
     * {@code readOnly = true} sadece okuma yapildigi icin performans ipucu — yazma islemi yapmaz.
     */
    @Transactional(readOnly = true)
    public List<Tablo> listTablolar() {
        return tabloRepository.findAll();
    }

    /** Id ile tek tablo bulur; yoksa 404'e cevrilecek {@link NotFoundException} firlatir (bkz. GlobalExceptionHandler). */
    @Transactional(readOnly = true)
    public Tablo getTablo(Long id) {
        return tabloRepository.findById(id)
                .orElseThrow(() -> new NotFoundException(
                        "NOT_FOUND_TABLE", "tablo not found: " + id, Map.of("id", String.valueOf(id))));
    }

    /**
     * Yeni tablo olusturur: once metadata (Tablo + Kolon satirlari) DB'ye yazilir, sonra ayni
     * transaction icinde gercek {@code CREATE TABLE} calistirilir. DDL patlarsa metadata insert'i
     * de otomatik geri alinir — iki katman asla birbirinden kopmaz.
     */
    @Transactional
    public Tablo createTablo(String name, List<KolonTanimi> kolonTanimlari) {
        NameValidator.validate("table name", "VALIDATION_INVALID_TABLE_NAME", name);
        if (tabloRepository.existsByName(name)) {
            throw new ConflictException(
                    "CONFLICT_DUPLICATE_TABLE_NAME",
                    "a table named '" + name + "' already exists",
                    Map.of("name", name));
        }
        if (kolonTanimlari == null || kolonTanimlari.isEmpty()) {
            throw new ValidationException("VALIDATION_TABLE_NEEDS_COLUMN", "a table needs at least one column");
        }

        Tablo tablo = new Tablo(name);
        Set<String> seenNames = new HashSet<>();
        List<TableDdlExecutor.ColumnDefinition> ddlColumns = new ArrayList<>();

        for (KolonTanimi tanim : kolonTanimlari) {
            NameValidator.validate("column name", "VALIDATION_INVALID_COLUMN_NAME", tanim.name());
            if (!seenNames.add(tanim.name())) {
                throw new ConflictException(
                        "CONFLICT_DUPLICATE_COLUMN_IN_REQUEST",
                        "duplicate column name in request: " + tanim.name(),
                        Map.of("name", tanim.name()));
            }
            ColumnType type = ColumnType.fromMetadataValue(tanim.type());

            Kolon kolon = new Kolon(tanim.name(), type.metadataValue(), tablo);
            kolon.setTag(resolveTag(tanim.tagId()));
            tablo.addKolon(kolon);
            ddlColumns.add(new TableDdlExecutor.ColumnDefinition(tanim.name(), type));
        }

        Tablo saved = tabloRepository.save(tablo);
        ddlExecutor.createTable(saved.getName(), ddlColumns);
        return saved;
    }

    /** Hem metadata'daki (Tablo.name) hem gercek Postgres tablosunun adini degistirir. */
    @Transactional
    public Tablo renameTablo(Long id, String newName) {
        Tablo tablo = getTablo(id);
        NameValidator.validate("table name", "VALIDATION_INVALID_TABLE_NAME", newName);
        if (!tablo.getName().equals(newName) && tabloRepository.existsByName(newName)) {
            throw new ConflictException(
                    "CONFLICT_DUPLICATE_TABLE_NAME",
                    "a table named '" + newName + "' already exists",
                    Map.of("name", newName));
        }
        String oldName = tablo.getName();
        tablo.setName(newName);
        ddlExecutor.renameTable(oldName, newName);
        return tablo;
    }

    /** Tablo silinince {@code tabloRepository.delete} JPA cascade sayesinde altindaki tum Kolon satirlarini da siler; sonra gercek tablo da drop edilir. */
    @Transactional
    public void deleteTablo(Long id) {
        Tablo tablo = getTablo(id);
        String name = tablo.getName();
        tabloRepository.delete(tablo);
        ddlExecutor.dropTable(name);
    }

    /** Mevcut bir tabloya yeni kolon ekler; metadata + gercek {@code ALTER TABLE ADD COLUMN} birlikte gider. */
    @Transactional
    public Kolon addKolon(Long tabloId, KolonTanimi tanim) {
        Tablo tablo = getTablo(tabloId);
        NameValidator.validate("column name", "VALIDATION_INVALID_COLUMN_NAME", tanim.name());
        if (kolonRepository.existsByTabloAndName(tablo, tanim.name())) {
            throw new ConflictException(
                    "CONFLICT_DUPLICATE_COLUMN_NAME",
                    "a column named '" + tanim.name() + "' already exists in this table",
                    Map.of("name", tanim.name()));
        }
        ColumnType type = ColumnType.fromMetadataValue(tanim.type());
        Kolon kolon = new Kolon(tanim.name(), type.metadataValue(), tablo);
        kolon.setTag(resolveTag(tanim.tagId()));
        tablo.addKolon(kolon);
        Kolon saved = kolonRepository.save(kolon);

        ddlExecutor.addColumn(tablo.getName(), saved.getName(), type);
        return saved;
    }

    /**
     * Dogrudan {@code kolonRepository.delete(...)} cagirmiyoruz — bilerek {@code tablo.removeKolon(kolon)}
     * kullaniyoruz, cunku Kolon'u Tablo'nun kendi listesinden cikarmak, Tablo entity'sindeki
     * {@code orphanRemoval = true} sayesinde otomatik DB'den silinmesini tetikler.
     */
    @Transactional
    public void deleteKolon(Long tabloId, Long kolonId) {
        Tablo tablo = getTablo(tabloId);
        Kolon kolon = findKolonInTablo(tablo, kolonId);
        String columnName = kolon.getName();
        tablo.removeKolon(kolon);
        ddlExecutor.dropColumn(tablo.getName(), columnName);
    }

    /** Kolonun sadece adini degistirir — tipi olusturulduktan sonra hic degistirilemez (bkz. Kolon.type, updatable=false). */
    @Transactional
    public Kolon renameKolon(Long tabloId, Long kolonId, String newName) {
        Tablo tablo = getTablo(tabloId);
        Kolon kolon = findKolonInTablo(tablo, kolonId);
        NameValidator.validate("column name", "VALIDATION_INVALID_COLUMN_NAME", newName);
        if (!kolon.getName().equals(newName) && kolonRepository.existsByTabloAndName(tablo, newName)) {
            throw new ConflictException(
                    "CONFLICT_DUPLICATE_COLUMN_NAME",
                    "a column named '" + newName + "' already exists in this table",
                    Map.of("name", newName));
        }
        String oldName = kolon.getName();
        kolon.setName(newName);
        ddlExecutor.renameColumn(tablo.getName(), oldName, newName);
        return kolon;
    }

    /** Tag'in gercek DB semasinda hic karsiligi yok (sadece metadata) — o yuzden burada ddlExecutor cagrisi yok, sade bir entity guncellemesi. */
    @Transactional
    public Kolon changeKolonTag(Long tabloId, Long kolonId, Long tagId) {
        Tablo tablo = getTablo(tabloId);
        Kolon kolon = findKolonInTablo(tablo, kolonId);
        kolon.setTag(resolveTag(tagId));
        return kolon;
    }

    /** Kolonu, tablonun zaten yuklu {@code kolonlar} listesi icinde arar (ekstra sorgu atmadan) — kolon bu tabloya ait degilse 404 doner. */
    private Kolon findKolonInTablo(Tablo tablo, Long kolonId) {
        return tablo.getKolonlar().stream()
                .filter(k -> k.getId().equals(kolonId))
                .findFirst()
                .orElseThrow(() -> new NotFoundException(
                        "NOT_FOUND_COLUMN",
                        "column not found in this table: " + kolonId,
                        Map.of("id", String.valueOf(kolonId))));
    }

    /** tagId null ise (kullanici tag secmediyse) null doner; doluysa o Tag'i bulur, yoksa 404. */
    private Tag resolveTag(Long tagId) {
        if (tagId == null) {
            return null;
        }
        return tagRepository.findById(tagId)
                .orElseThrow(() -> new NotFoundException(
                        "NOT_FOUND_TAG", "tag not found: " + tagId, Map.of("id", String.valueOf(tagId))));
    }
}

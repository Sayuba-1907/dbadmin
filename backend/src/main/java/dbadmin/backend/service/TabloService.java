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
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

    @Transactional(readOnly = true)
    public List<Tablo> listTablolar() {
        return tabloRepository.findAll();
    }

    @Transactional(readOnly = true)
    public Tablo getTablo(Long id) {
        return tabloRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("tablo not found: " + id));
    }

    // Metadata write + real CREATE TABLE happen in one transaction: if the
    // DDL fails, the metadata insert is rolled back too, so the two layers
    // (Tablo/Kolon rows vs. the real table) never drift apart.
    @Transactional
    public Tablo createTablo(String name, List<KolonTanimi> kolonTanimlari) {
        NameValidator.validate("table name", name);
        if (tabloRepository.existsByName(name)) {
            throw new ConflictException("a table named '" + name + "' already exists");
        }
        if (kolonTanimlari == null || kolonTanimlari.isEmpty()) {
            throw new ValidationException("a table needs at least one column");
        }

        Tablo tablo = new Tablo(name);
        Set<String> seenNames = new HashSet<>();
        List<TableDdlExecutor.ColumnDefinition> ddlColumns = new ArrayList<>();

        for (KolonTanimi tanim : kolonTanimlari) {
            NameValidator.validate("column name", tanim.name());
            if (!seenNames.add(tanim.name())) {
                throw new ConflictException("duplicate column name in request: " + tanim.name());
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

    @Transactional
    public Tablo renameTablo(Long id, String newName) {
        Tablo tablo = getTablo(id);
        NameValidator.validate("table name", newName);
        if (!tablo.getName().equals(newName) && tabloRepository.existsByName(newName)) {
            throw new ConflictException("a table named '" + newName + "' already exists");
        }
        String oldName = tablo.getName();
        tablo.setName(newName);
        ddlExecutor.renameTable(oldName, newName);
        return tablo;
    }

    @Transactional
    public void deleteTablo(Long id) {
        Tablo tablo = getTablo(id);
        String name = tablo.getName();
        tabloRepository.delete(tablo);
        ddlExecutor.dropTable(name);
    }

    @Transactional
    public Kolon addKolon(Long tabloId, KolonTanimi tanim) {
        Tablo tablo = getTablo(tabloId);
        NameValidator.validate("column name", tanim.name());
        if (kolonRepository.existsByTabloAndName(tablo, tanim.name())) {
            throw new ConflictException("a column named '" + tanim.name() + "' already exists in this table");
        }
        ColumnType type = ColumnType.fromMetadataValue(tanim.type());

        Kolon kolon = new Kolon(tanim.name(), type.metadataValue(), tablo);
        kolon.setTag(resolveTag(tanim.tagId()));
        tablo.addKolon(kolon);
        Kolon saved = kolonRepository.save(kolon);

        ddlExecutor.addColumn(tablo.getName(), saved.getName(), type);
        return saved;
    }

    // Removing from Tablo's managed collection (rather than calling
    // kolonRepository.delete directly) is what triggers orphanRemoval -
    // see Tablo.removeKolon and the cascade config on its @OneToMany.
    @Transactional
    public void deleteKolon(Long tabloId, Long kolonId) {
        Tablo tablo = getTablo(tabloId);
        Kolon kolon = findKolonInTablo(tablo, kolonId);
        String columnName = kolon.getName();
        tablo.removeKolon(kolon);
        ddlExecutor.dropColumn(tablo.getName(), columnName);
    }

    @Transactional
    public Kolon renameKolon(Long tabloId, Long kolonId, String newName) {
        Tablo tablo = getTablo(tabloId);
        Kolon kolon = findKolonInTablo(tablo, kolonId);
        NameValidator.validate("column name", newName);
        if (!kolon.getName().equals(newName) && kolonRepository.existsByTabloAndName(tablo, newName)) {
            throw new ConflictException("a column named '" + newName + "' already exists in this table");
        }
        String oldName = kolon.getName();
        kolon.setName(newName);
        ddlExecutor.renameColumn(tablo.getName(), oldName, newName);
        return kolon;
    }

    // Tag has no DDL counterpart - it only ever lived in metadata, so this
    // is a plain entity update, no TableDdlExecutor call.
    @Transactional
    public Kolon changeKolonTag(Long tabloId, Long kolonId, Long tagId) {
        Tablo tablo = getTablo(tabloId);
        Kolon kolon = findKolonInTablo(tablo, kolonId);
        kolon.setTag(resolveTag(tagId));
        return kolon;
    }

    private Kolon findKolonInTablo(Tablo tablo, Long kolonId) {
        return tablo.getKolonlar().stream()
                .filter(k -> k.getId().equals(kolonId))
                .findFirst()
                .orElseThrow(() -> new NotFoundException("column not found in this table: " + kolonId));
    }

    private Tag resolveTag(Long tagId) {
        if (tagId == null) {
            return null;
        }
        return tagRepository.findById(tagId)
                .orElseThrow(() -> new NotFoundException("tag not found: " + tagId));
    }
}

package dbadmin.backend.service;

import dbadmin.backend.aop.BusinessLog;
import dbadmin.backend.entity.DataColumn;
import dbadmin.backend.entity.OperationType;
import dbadmin.backend.entity.Tag;
import dbadmin.backend.entity.TargetType;
import dbadmin.backend.exception.ConflictException;
import dbadmin.backend.exception.NotFoundException;
import dbadmin.backend.repository.ColumnRepository;
import dbadmin.backend.repository.TagRepository;
import dbadmin.backend.validation.NameValidator;
import java.util.List;
import java.util.Map;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Tag'lerin is mantigi. Tag'lerin (DataColumn'un aksine) gercek DB semasinda karsiligi olmadigi icin bu servis DDL katmanina hic dokunmaz, sade CRUD. */
@Service
public class TagService {

    private final TagRepository tagRepository;
    private final ColumnRepository columnRepository;
    private final AuditLogService auditLogService;

    public TagService(TagRepository tagRepository, ColumnRepository columnRepository,
            AuditLogService auditLogService) {
        this.tagRepository = tagRepository;
        this.columnRepository = columnRepository;
        this.auditLogService = auditLogService;
    }

    /**
     * BILEREK {@code @Cacheable} YOK (once vardi, Pageable eklenirken kaldirildi): Redis'e
     * yazilan deger burada artik {@code Page<Tag>} (once duz {@code List<Tag>}) — canli olarak
     * denendi, {@code GenericJackson2JsonRedisSerializer} {@code PageImpl}'i YAZABILIYOR ama geri
     * OKUYAMIYOR ({@code PageImpl}'in Jackson'in kullanabilecegi bir constructor'i yok:
     * "Cannot construct instance of PageImpl (no Creators...)"). Sonuc: her okuma sessizce
     * (CacheConfig'teki fail-open error handler sayesinde hic hata firlamadan) DB'ye dusuyordu —
     * yani cache hicbir sey kazandirmiyor, sadece her istekte bosuna bir Redis round-trip'i +
     * WARN log satiri ekliyordu. Duzeltmek (ör. PageImpl icin ozel bir Jackson mixin/creator
     * yazmak) bu listenin boyutuna (Tag sayisi tipik olarak onlarca, binler degil) gore
     * degmeyecek bir karmasiklik; o yuzden cache'i kaldirmak, "duzeltilmis ama kirik gibi
     * davranan" bir @Cacheable birakmaktan daha durust bir cozum.
     */
    @Transactional(readOnly = true)
    public Page<Tag> listTags(Pageable pageable) {
        return tagRepository.findAll(pageable);
    }

    /** Id ile tek tag bulur; yoksa 404'e cevrilecek {@link NotFoundException} firlatir. */
    @Transactional(readOnly = true)
    public Tag getTag(Long id) {
        return tagRepository.findById(id)
                .orElseThrow(() -> new NotFoundException(
                        "NOT_FOUND_TAG", "tag not found: " + id, Map.of("id", String.valueOf(id))));
    }

    /**
     * Bu tag'i tasiyan tum kolonlari (ve onlarin tablo/schema bilgisini) doner — sol menudeki
     * "Tagler" gorunumunde bir tag'in ayrinti butonuna basildiginda "hangi tablonun hangi
     * kolonunda kullaniliyor" sorusunu cevaplamak icin. Tag id'si gecersizse 404.
     */
    @Transactional(readOnly = true)
    public List<DataColumn> getTagUsage(Long tagId) {
        getTag(tagId);
        return columnRepository.findByTagId(tagId);
    }

    @BusinessLog("tag-olusturuldu")
    @Transactional
    public Tag createTag(String name) {
        NameValidator.validate("tag name", "VALIDATION_INVALID_TAG_NAME", name);
        if (tagRepository.existsByName(name)) {
            throw new ConflictException(
                    "CONFLICT_DUPLICATE_TAG_NAME", "a tag named '" + name + "' already exists", Map.of("name", name));
        }
        Tag saved = tagRepository.save(new Tag(name));
        auditLogService.record(OperationType.TAG_CREATED, TargetType.TAG, saved.getId(),
                "tag olusturuldu: " + saved.getName());
        return saved;
    }

    /** Sadece ismi degistirir — Tag'in gercek Postgres semasinda karsiligi olmadigi icin DDL calismaz. */
    @Transactional
    public Tag renameTag(Long id, String name) {
        Tag tag = getTag(id);
        NameValidator.validate("tag name", "VALIDATION_INVALID_TAG_NAME", name);
        if (!tag.getName().equals(name) && tagRepository.existsByName(name)) {
            throw new ConflictException(
                    "CONFLICT_DUPLICATE_TAG_NAME", "a tag named '" + name + "' already exists", Map.of("name", name));
        }
        String oldName = tag.getName();
        tag.setName(name);
        Tag saved = tagRepository.save(tag);
        if (!oldName.equals(name)) {
            auditLogService.record(OperationType.TAG_RENAMED, TargetType.TAG, id,
                    "isim degisti: " + oldName + " -> " + name);
        }
        return saved;
    }

    /**
     * Tag'i siler. DataColumn->Tag iliskisinde cascade YOK (bkz. {@link DataColumn#getTag()}) — yani
     * Postgres FK constraint'i yuzunden, tag'i kullanan kolonlar varken direkt silmeye
     * calisilirsa {@code DataIntegrityViolationException} firlar. Onun yerine, tag'i kullanan
     * tum kolonlarin tag referansini once null'a cekiyoruz (etiketsiz kalirlar, kolonun kendisi
     * silinmez), sonra tag satirini siliyoruz — hepsi tek transaction'da.
     */
    @BusinessLog("tag-silindi")
    @Transactional
    public void deleteTag(Long id) {
        Tag tag = getTag(id);
        List<DataColumn> columnsUsingTag = columnRepository.findByTagId(id);
        columnsUsingTag.forEach(column -> column.setTag(null));
        columnRepository.saveAll(columnsUsingTag);
        tagRepository.delete(tag);
        auditLogService.record(OperationType.TAG_DELETED, TargetType.TAG, id, "tag silindi: " + tag.getName());
    }
}

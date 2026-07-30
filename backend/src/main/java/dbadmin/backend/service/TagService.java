package dbadmin.backend.service;

import dbadmin.backend.entity.Kolon;
import dbadmin.backend.entity.Tag;
import dbadmin.backend.exception.ConflictException;
import dbadmin.backend.exception.NotFoundException;
import dbadmin.backend.repository.KolonRepository;
import dbadmin.backend.repository.TagRepository;
import dbadmin.backend.validation.NameValidator;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Tag'lerin is mantigi. Tag'lerin (Kolon'un aksine) gercek DB semasinda karsiligi olmadigi icin bu servis DDL katmanina hic dokunmaz, sade CRUD. */
@Service
public class TagService {

    private final TagRepository tagRepository;
    private final KolonRepository kolonRepository;

    public TagService(TagRepository tagRepository, KolonRepository kolonRepository) {
        this.tagRepository = tagRepository;
        this.kolonRepository = kolonRepository;
    }

    @Transactional(readOnly = true)
    public List<Tag> listTags() {
        return tagRepository.findAll();
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
    public List<Kolon> getTagUsage(Long tagId) {
        getTag(tagId);
        return kolonRepository.findByTagId(tagId);
    }

    @Transactional
    public Tag createTag(String name) {
        NameValidator.validate("tag name", "VALIDATION_INVALID_TAG_NAME", name);
        if (tagRepository.existsByName(name)) {
            throw new ConflictException(
                    "CONFLICT_DUPLICATE_TAG_NAME", "a tag named '" + name + "' already exists", Map.of("name", name));
        }
        return tagRepository.save(new Tag(name));
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
        tag.setName(name);
        return tagRepository.save(tag);
    }

    /**
     * Tag'i siler. Kolon->Tag iliskisinde cascade YOK (bkz. {@link Kolon#getTag()}) — yani
     * Postgres FK constraint'i yuzunden, tag'i kullanan kolonlar varken direkt silmeye
     * calisilirsa {@code DataIntegrityViolationException} firlar. Onun yerine, tag'i kullanan
     * tum kolonlarin tag referansini once null'a cekiyoruz (etiketsiz kalirlar, kolonun kendisi
     * silinmez), sonra tag satirini siliyoruz — hepsi tek transaction'da.
     */
    @Transactional
    public void deleteTag(Long id) {
        Tag tag = getTag(id);
        List<Kolon> kullananKolonlar = kolonRepository.findByTagId(id);
        kullananKolonlar.forEach(kolon -> kolon.setTag(null));
        kolonRepository.saveAll(kullananKolonlar);
        tagRepository.delete(tag);
    }
}

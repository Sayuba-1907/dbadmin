package dbadmin.backend.service;

import dbadmin.backend.entity.Tag;
import dbadmin.backend.exception.ConflictException;
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

    public TagService(TagRepository tagRepository) {
        this.tagRepository = tagRepository;
    }

    @Transactional(readOnly = true)
    public List<Tag> listTags() {
        return tagRepository.findAll();
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
}

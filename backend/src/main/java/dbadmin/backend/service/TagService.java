package dbadmin.backend.service;

import dbadmin.backend.entity.Tag;
import dbadmin.backend.exception.ConflictException;
import dbadmin.backend.repository.TagRepository;
import dbadmin.backend.validation.NameValidator;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
        NameValidator.validate("tag name", name);
        if (tagRepository.existsByName(name)) {
            throw new ConflictException("a tag named '" + name + "' already exists");
        }
        return tagRepository.save(new Tag(name));
    }
}

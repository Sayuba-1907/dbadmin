package dbadmin.backend.controller;

import dbadmin.backend.dto.CreateTagRequest;
import dbadmin.backend.dto.TagResponse;
import dbadmin.backend.service.TagService;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Tag endpoint'leri — TabloController'a benzer ince bir katman, is mantigi {@link TagService}'te. */
@RestController
@RequestMapping("/api/tags")
public class TagController {

    private final TagService tagService;

    public TagController(TagService tagService) {
        this.tagService = tagService;
    }

    /** GET /api/tags — tum etiketlerin listesi. */
    @GetMapping
    public List<TagResponse> list() {
        return tagService.listTags().stream().map(TagResponse::from).toList();
    }

    /** POST /api/tags — yeni etiket olusturur, 201 Created doner. */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TagResponse create(@RequestBody CreateTagRequest request) {
        return TagResponse.from(tagService.createTag(request.name()));
    }
}

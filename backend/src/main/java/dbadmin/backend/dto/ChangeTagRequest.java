package dbadmin.backend.dto;
//ChangeTagRequest, bir kolonun etiketini değiştirmek istediğimizde dış
// dünyadan gelen "hangi etiket takılacak?" bilgisini taşıyan küçücük, tertemiz bir kurye paketidir.
// tagId == null clears the column's tag; a non-null id points at the tag to attach.
public record ChangeTagRequest(Long tagId) {
}

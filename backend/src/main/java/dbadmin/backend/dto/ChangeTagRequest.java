package dbadmin.backend.dto;
/** Kolonun etiketini degistirme request'i. {@code tagId == null} ise etiket kaldirilir; doluysa o etikete baglanir. */
public record ChangeTagRequest(Long tagId) {
}

package dbadmin.backend.dto;

// Shared body shape for "rename table" and "rename column" - both take a
// single new name string.
//RenameRequest, adı değiştirilmek istenen bir nesne için dış dünyadan gelen yeni ismi taşıyan en pratik ortak formdur.
public record RenameRequest(String name) {
}

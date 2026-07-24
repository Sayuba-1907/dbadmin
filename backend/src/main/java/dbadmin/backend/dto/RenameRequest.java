package dbadmin.backend.dto;

/** "Rename table" ve "rename column" endpoint'lerinin ortak request govdesi — ikisi de tek bir yeni isim alir. */
public record RenameRequest(String name) {
}

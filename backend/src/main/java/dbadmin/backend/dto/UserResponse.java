package dbadmin.backend.dto;

import dbadmin.backend.entity.Role;
import dbadmin.backend.entity.User;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Bir kullanicinin disariya donen hali. Parola hash'i <b>bilerek yok</b> — entity'yi
 * dogrudan dondurseydik hash de JSON'a girerdi.
 */
public record UserResponse(
        @Schema(description = "Kullanicinin id'si.", example = "1") Long id,
        @Schema(description = "Kullanici adi.", example = "admin") String username,
        @Schema(description = "Rolu.", example = "ADMIN") Role role) {

    public static UserResponse from(User user) {
        return new UserResponse(user.getId(), user.getUsername(), user.getRole());
    }
}

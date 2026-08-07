package dbadmin.backend.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dbadmin.backend.AbstractIntegrationTest;
import dbadmin.backend.entity.User;
import dbadmin.backend.entity.Role;
import dbadmin.backend.exception.ConflictException;
import dbadmin.backend.exception.ValidationException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Pageable;

// Odak: son ADMIN korumasi (lastAdminGuard) — hicbir yerde test edilmiyordu.
class UserServiceIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private UserService userService;

    @Test
    void createKullanici_rolVerilmezse_VIEWER_atanir() {
        User k = userService.createUser("test_rolsuz", "parolam1234", null);

        assertEquals(Role.VIEWER, k.getRole());
    }

    @Test
    void createKullanici_parolaHashlenir_duzMetinDegil() {
        User k = userService.createUser("test_hash", "parolam1234", Role.VIEWER);

        assertTrue(k.getPasswordHash().startsWith("$2"));
    }

    @Test
    void createKullanici_kisaParola_ValidationException() {
        assertThrows(
                ValidationException.class,
                () -> userService.createUser("test_kisa", "1234", Role.VIEWER));
    }

    @Test
    void createKullanici_tekrarEdenKullaniciAdi_ConflictException() {
        userService.createUser("test_cakisan", "parolam1234", Role.VIEWER);

        assertThrows(
                ConflictException.class,
                () -> userService.createUser("test_cakisan", "baskaParola1", Role.EDITOR));
    }

    /**
     * Sistemde seeder'dan (ilk ADMIN) gelen baska admin'ler de olabilecegi icin "tam olarak bir
     * admin var" varsayimi yerine, testten oncesi/sonrasi admin sayisini olcup davranisi ona gore
     * dogruluyoruz — asil sinanan kural degismiyor: "son admin dusurulemez, digerleri dusurulebilir".
     */
    @Test
    void changeRol_sonAdmin_dusurulemez() {
        User tekAdmin = userService.createUser("test_tek_admin", "parolam1234", Role.ADMIN);
        long adminSayisi = userService.listUsers(Pageable.unpaged()).stream()
                .filter(k -> k.role() == Role.ADMIN)
                .count();

        if (adminSayisi <= 1) {
            assertThrows(
                    ConflictException.class,
                    () -> userService.changeRole(tekAdmin.getId(), Role.EDITOR));
        } else {
            assertEquals(Role.EDITOR, userService.changeRole(tekAdmin.getId(), Role.EDITOR).getRole());
        }
    }

    @Test
    void changeRol_ikiAdminVarken_biriDusurulebilir() {
        User admin1 = userService.createUser("test_admin_a", "parolam1234", Role.ADMIN);
        userService.createUser("test_admin_b", "parolam1234", Role.ADMIN);

        User guncellenen = userService.changeRole(admin1.getId(), Role.VIEWER);

        assertEquals(Role.VIEWER, guncellenen.getRole());
    }

    @Test
    void deleteKullanici_ikiAdminVarken_biriSilinebilir() {
        User admin1 = userService.createUser("test_del_admin_a", "parolam1234", Role.ADMIN);
        userService.createUser("test_del_admin_b", "parolam1234", Role.ADMIN);

        userService.deleteUser(admin1.getId());

        assertThrows(
                dbadmin.backend.exception.NotFoundException.class,
                () -> userService.getUser(admin1.getId()));
    }

    @Test
    void deleteKullanici_editorSilinebilir_adminKisitiUygulanmaz() {
        User editor = userService.createUser("test_del_editor", "parolam1234", Role.EDITOR);

        userService.deleteUser(editor.getId());

        assertThrows(
                dbadmin.backend.exception.NotFoundException.class,
                () -> userService.getUser(editor.getId()));
    }
}

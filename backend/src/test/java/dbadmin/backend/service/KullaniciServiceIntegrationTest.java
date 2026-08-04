package dbadmin.backend.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dbadmin.backend.AbstractIntegrationTest;
import dbadmin.backend.entity.Kullanici;
import dbadmin.backend.entity.Rol;
import dbadmin.backend.exception.ConflictException;
import dbadmin.backend.exception.ValidationException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

// Odak: son ADMIN korumasi (sonAdminKorumasi) — hicbir yerde test edilmiyordu.
class KullaniciServiceIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private KullaniciService kullaniciService;

    @Test
    void createKullanici_rolVerilmezse_VIEWER_atanir() {
        Kullanici k = kullaniciService.createKullanici("test_rolsuz", "parolam1234", null);

        assertEquals(Rol.VIEWER, k.getRol());
    }

    @Test
    void createKullanici_parolaHashlenir_duzMetinDegil() {
        Kullanici k = kullaniciService.createKullanici("test_hash", "parolam1234", Rol.VIEWER);

        assertTrue(k.getParolaHash().startsWith("$2"));
    }

    @Test
    void createKullanici_kisaParola_ValidationException() {
        assertThrows(
                ValidationException.class,
                () -> kullaniciService.createKullanici("test_kisa", "1234", Rol.VIEWER));
    }

    @Test
    void createKullanici_tekrarEdenKullaniciAdi_ConflictException() {
        kullaniciService.createKullanici("test_cakisan", "parolam1234", Rol.VIEWER);

        assertThrows(
                ConflictException.class,
                () -> kullaniciService.createKullanici("test_cakisan", "baskaParola1", Rol.EDITOR));
    }

    /**
     * Sistemde seeder'dan (ilk ADMIN) gelen baska admin'ler de olabilecegi icin "tam olarak bir
     * admin var" varsayimi yerine, testten oncesi/sonrasi admin sayisini olcup davranisi ona gore
     * dogruluyoruz — asil sinanan kural degismiyor: "son admin dusurulemez, digerleri dusurulebilir".
     */
    @Test
    void changeRol_sonAdmin_dusurulemez() {
        Kullanici tekAdmin = kullaniciService.createKullanici("test_tek_admin", "parolam1234", Rol.ADMIN);
        long adminSayisi = kullaniciService.listKullanicilar().stream()
                .filter(k -> k.rol() == Rol.ADMIN)
                .count();

        if (adminSayisi <= 1) {
            assertThrows(
                    ConflictException.class,
                    () -> kullaniciService.changeRol(tekAdmin.getId(), Rol.EDITOR));
        } else {
            assertEquals(Rol.EDITOR, kullaniciService.changeRol(tekAdmin.getId(), Rol.EDITOR).getRol());
        }
    }

    @Test
    void changeRol_ikiAdminVarken_biriDusurulebilir() {
        Kullanici admin1 = kullaniciService.createKullanici("test_admin_a", "parolam1234", Rol.ADMIN);
        kullaniciService.createKullanici("test_admin_b", "parolam1234", Rol.ADMIN);

        Kullanici guncellenen = kullaniciService.changeRol(admin1.getId(), Rol.VIEWER);

        assertEquals(Rol.VIEWER, guncellenen.getRol());
    }

    @Test
    void deleteKullanici_ikiAdminVarken_biriSilinebilir() {
        Kullanici admin1 = kullaniciService.createKullanici("test_del_admin_a", "parolam1234", Rol.ADMIN);
        kullaniciService.createKullanici("test_del_admin_b", "parolam1234", Rol.ADMIN);

        kullaniciService.deleteKullanici(admin1.getId());

        assertThrows(
                dbadmin.backend.exception.NotFoundException.class,
                () -> kullaniciService.getKullanici(admin1.getId()));
    }

    @Test
    void deleteKullanici_editorSilinebilir_adminKisitiUygulanmaz() {
        Kullanici editor = kullaniciService.createKullanici("test_del_editor", "parolam1234", Rol.EDITOR);

        kullaniciService.deleteKullanici(editor.getId());

        assertThrows(
                dbadmin.backend.exception.NotFoundException.class,
                () -> kullaniciService.getKullanici(editor.getId()));
    }
}

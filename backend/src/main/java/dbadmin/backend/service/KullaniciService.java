package dbadmin.backend.service;

import dbadmin.backend.entity.Kullanici;
import dbadmin.backend.entity.Rol;
import dbadmin.backend.exception.ConflictException;
import dbadmin.backend.exception.NotFoundException;
import dbadmin.backend.exception.ValidationException;
import dbadmin.backend.repository.KullaniciRepository;
import dbadmin.backend.validation.NameValidator;
import java.util.List;
import java.util.Map;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Kullanici yonetiminin is mantigi (sadece ADMIN'in ulasabildigi uclarin arkasi).
 * Parolanin duz metinden hash'e cevrildigi tek yer burasidir — entity hash'i oldugu gibi
 * saklar, controller ise duz parolayi hic gormeden buraya gecirir.
 */
@Service
public class KullaniciService {

    /** BCrypt'in kendi siniri 72 bayt; altindaki her sey kullaniciyi zorlamayan bir alt sinir. */
    private static final int MIN_PAROLA_UZUNLUGU = 8;

    private final KullaniciRepository kullaniciRepository;
    private final PasswordEncoder passwordEncoder;

    public KullaniciService(KullaniciRepository kullaniciRepository, PasswordEncoder passwordEncoder) {
        this.kullaniciRepository = kullaniciRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional(readOnly = true)
    public List<Kullanici> listKullanicilar() {
        return kullaniciRepository.findAll();
    }

    @Transactional(readOnly = true)
    public Kullanici getKullanici(Long id) {
        return kullaniciRepository.findById(id)
                .orElseThrow(() -> new NotFoundException(
                        "NOT_FOUND_USER", "user not found: " + id, Map.of("id", String.valueOf(id))));
    }

    @Transactional(readOnly = true)
    public Kullanici getKullaniciByAd(String kullaniciAdi) {
        return kullaniciRepository.findByKullaniciAdi(kullaniciAdi)
                .orElseThrow(() -> new NotFoundException(
                        "NOT_FOUND_USER", "user not found: " + kullaniciAdi,
                        Map.of("id", String.valueOf(kullaniciAdi))));
    }

    /** Rol verilmezse en dusuk yetkiyle (VIEWER) baslar — yeni kullanici kazara yazma yetkisi almasin. */
    @Transactional
    public Kullanici createKullanici(String kullaniciAdi, String parola, Rol rol) {
        NameValidator.validate("username", "VALIDATION_INVALID_USERNAME", kullaniciAdi);
        parolaDogrula(parola);

        if (kullaniciRepository.existsByKullaniciAdi(kullaniciAdi)) {
            throw new ConflictException(
                    "CONFLICT_DUPLICATE_USERNAME",
                    "a user named '" + kullaniciAdi + "' already exists",
                    Map.of("name", kullaniciAdi));
        }

        Rol atanacak = rol == null ? Rol.VIEWER : rol;
        return kullaniciRepository.save(
                new Kullanici(kullaniciAdi, passwordEncoder.encode(parola), atanacak));
    }

    @Transactional
    public Kullanici changeRol(Long id, Rol yeniRol) {
        if (yeniRol == null) {
            throw new ValidationException("VALIDATION_MISSING_ROLE", "a role must be specified");
        }
        Kullanici kullanici = getKullanici(id);
        sonAdminKorumasi(kullanici, yeniRol);
        kullanici.setRol(yeniRol);
        return kullanici;
    }

    @Transactional
    public void deleteKullanici(Long id) {
        Kullanici kullanici = getKullanici(id);
        sonAdminKorumasi(kullanici, null);
        kullaniciRepository.delete(kullanici);
    }

    /**
     * Sistemde en az bir ADMIN kalmasini garanti eder. Bu kontrol olmasaydi tek admin kendi
     * rolunu dusurup ya da kendini silip <b>kullanici yonetimine bir daha girilemez</b> hale
     * getirebilirdi — geri donusu sadece veritabanina elle mudahaleyle olan bir kilitlenme.
     *
     * @param yeniRol rol degisikligi icin yeni rol; silme isleminde {@code null}
     */
    private void sonAdminKorumasi(Kullanici kullanici, Rol yeniRol) {
        boolean adminliktenCikiyor = kullanici.getRol() == Rol.ADMIN && yeniRol != Rol.ADMIN;
        if (!adminliktenCikiyor) {
            return;
        }
        long adminSayisi = kullaniciRepository.findAll().stream()
                .filter(k -> k.getRol() == Rol.ADMIN)
                .count();
        if (adminSayisi <= 1) {
            throw new ConflictException(
                    "CONFLICT_LAST_ADMIN", "the last remaining admin cannot be removed or demoted");
        }
    }

    private void parolaDogrula(String parola) {
        if (parola == null || parola.length() < MIN_PAROLA_UZUNLUGU) {
            throw new ValidationException(
                    "VALIDATION_WEAK_PASSWORD",
                    "password must be at least " + MIN_PAROLA_UZUNLUGU + " characters",
                    Map.of("min", String.valueOf(MIN_PAROLA_UZUNLUGU)));
        }
    }
}

package dbadmin.backend.config;

import dbadmin.backend.entity.Rol;
import dbadmin.backend.repository.KullaniciRepository;
import dbadmin.backend.service.KullaniciService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * Ilk ADMIN kullanicisini olusturur.
 *
 * <h2>Neden gerekli?</h2>
 * Kullanici yaratmak ADMIN yetkisi ister, ama sistemde hic kullanici yokken kimse giris
 * yapamaz — klasik "tavuk mu yumurta mi" problemi. Bu sinif onu kirar: veritabaninda
 * <b>hic kullanici yoksa</b> ayarlardan okudugu bilgilerle bir ADMIN yaratir.
 *
 * <h2>Neden migration script'i degil?</h2>
 * Parola BCrypt ile hash'lenmeli ve BCrypt her seferinde farkli (rastgele salt'li) bir cikti
 * uretir — SQL dosyasina sabit bir hash yazmak, o hash'i ureten parolayi da depoya commit'lemek
 * demekti. Burada hash uygulama acilirken uretilir, parola sadece ortam degiskeninde yasar.
 *
 * <p>Kosul {@code count() == 0}: yalnizca bos bir veritabaninda calisir. Yani mevcut bir
 * kurulumda admin'in parolasini sifirlamaz, silinen bir admin'i geri getirmez.
 */
@Component
public class KullaniciSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(KullaniciSeeder.class);

    private final KullaniciRepository kullaniciRepository;
    private final KullaniciService kullaniciService;
    private final String baslangicKullaniciAdi;
    private final String baslangicParola;

    public KullaniciSeeder(
            KullaniciRepository kullaniciRepository,
            KullaniciService kullaniciService,
            @Value("${app.security.initial-admin.username}") String baslangicKullaniciAdi,
            @Value("${app.security.initial-admin.password}") String baslangicParola) {
        this.kullaniciRepository = kullaniciRepository;
        this.kullaniciService = kullaniciService;
        this.baslangicKullaniciAdi = baslangicKullaniciAdi;
        this.baslangicParola = baslangicParola;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (kullaniciRepository.count() > 0) {
            return;
        }
        kullaniciService.createKullanici(baslangicKullaniciAdi, baslangicParola, Rol.ADMIN);
        // Parola loglanmaz; sadece hangi kullanicinin yaratildigi ve neden.
        log.warn("Hic kullanici yoktu, baslangic ADMIN'i olusturuldu: '{}'. "
                + "Uretimde app.security.initial-admin.password mutlaka ortam degiskeninden verilmeli "
                + "ve ilk giristen sonra degistirilmelidir.", baslangicKullaniciAdi);
    }
}

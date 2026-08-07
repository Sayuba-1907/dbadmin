package dbadmin.backend.config;

import dbadmin.backend.entity.Role;
import dbadmin.backend.repository.UserRepository;
import dbadmin.backend.service.UserService;
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
public class UserSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(UserSeeder.class);

    private final UserRepository userRepository;
    private final UserService userService;
    private final String initialUsername;
    private final String initialPassword;

    public UserSeeder(
            UserRepository userRepository,
            UserService userService,
            @Value("${app.security.initial-admin.username}") String initialUsername,
            @Value("${app.security.initial-admin.password}") String initialPassword) {
        this.userRepository = userRepository;
        this.userService = userService;
        this.initialUsername = initialUsername;
        this.initialPassword = initialPassword;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (userRepository.count() > 0) {
            return;
        }
        userService.createUser(initialUsername, initialPassword, Role.ADMIN);
        // Parola loglanmaz; sadece hangi kullanicinin yaratildigi ve neden.
        log.warn("Hic kullanici yoktu, baslangic ADMIN'i olusturuldu: '{}'. "
                + "Uretimde app.security.initial-admin.password mutlaka ortam degiskeninden verilmeli "
                + "ve ilk giristen sonra degistirilmelidir.", initialUsername);
    }
}

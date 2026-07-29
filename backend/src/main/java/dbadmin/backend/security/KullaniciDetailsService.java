package dbadmin.backend.security;

import dbadmin.backend.entity.Kullanici;
import dbadmin.backend.repository.KullaniciRepository;
import java.util.List;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

/**
 * Spring Security ile veritabanimiz arasindaki koprü: Security "su kullanici adini bul"
 * dediginde ({@code UserDetailsService}), biz {@code kullanici} tablosuna bakip cevabi
 * Spring'in anladigi {@link UserDetails} sekline ceviririz.
 * <p>
 * Parola karsilastirmasini biz yapmayiz — hash'i {@link UserDetails}'e koyariz, dogrulamayi
 * {@code AuthenticationManager} yapilandirilmis {@code PasswordEncoder} ile yapar
 * (bkz. {@link SecurityConfig}).
 */
@Service
public class KullaniciDetailsService implements UserDetailsService {

    private final KullaniciRepository kullaniciRepository;

    public KullaniciDetailsService(KullaniciRepository kullaniciRepository) {
        this.kullaniciRepository = kullaniciRepository;
    }

    /**
     * Kullanici bulunamazsa {@link UsernameNotFoundException} firlatir. Spring bunu disariya
     * "bad credentials" olarak yansitir — yani istemci "boyle bir kullanici yok" ile "parola
     * yanlis" arasindaki farki goremez. Bu bilerek boyledir: aksi halde hangi kullanici
     * adlarinin var oldugu tek tek denenerek ogrenilebilirdi.
     */
    @Override
    public UserDetails loadUserByUsername(String kullaniciAdi) {
        Kullanici kullanici = kullaniciRepository
                .findByKullaniciAdi(kullaniciAdi)
                .orElseThrow(() -> new UsernameNotFoundException("kullanici bulunamadi"));

        return new User(
                kullanici.getKullaniciAdi(),
                kullanici.getParolaHash(),
                List.of(new SimpleGrantedAuthority(kullanici.getRol().authority())));
    }
}

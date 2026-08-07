package dbadmin.backend.security;

import dbadmin.backend.entity.User;
import dbadmin.backend.repository.UserRepository;
import java.util.List;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

/**
 * Spring Security ile veritabanimiz arasindaki koprü: Security "su kullanici adini bul"
 * dediginde ({@code UserDetailsService}), biz {@code users} tablosuna bakip cevabi
 * Spring'in anladigi {@link UserDetails} sekline ceviririz. Sinif adi bilerek {@code
 * UserDetailsServiceImpl}: {@code dbadmin.backend.entity.User} ile Spring Security'nin kendi
 * {@code org.springframework.security.core.userdetails.User} sinifi bu dosyada bir arada
 * kullanildigi icin (asagida fully-qualified referansla ayristirildi) kafa karistiran bir isim
 * secilmedi.
 * <p>
 * Parola karsilastirmasini biz yapmayiz — hash'i {@link UserDetails}'e koyariz, dogrulamayi
 * {@code AuthenticationManager} yapilandirilmis {@code PasswordEncoder} ile yapar
 * (bkz. {@link SecurityConfig}).
 */
@Service
public class UserDetailsServiceImpl implements UserDetailsService {

    private final UserRepository userRepository;

    public UserDetailsServiceImpl(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    /**
     * Kullanici bulunamazsa {@link UsernameNotFoundException} firlatir. Spring bunu disariya
     * "bad credentials" olarak yansitir — yani istemci "boyle bir kullanici yok" ile "parola
     * yanlis" arasindaki farki goremez. Bu bilerek boyledir: aksi halde hangi kullanici
     * adlarinin var oldugu tek tek denenerek ogrenilebilirdi.
     */
    @Override
    public UserDetails loadUserByUsername(String username) {
        User user = userRepository
                .findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("kullanici bulunamadi"));

        return new org.springframework.security.core.userdetails.User(
                user.getUsername(),
                user.getPasswordHash(),
                List.of(new SimpleGrantedAuthority(user.getRole().authority())));
    }
}

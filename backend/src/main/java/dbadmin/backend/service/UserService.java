package dbadmin.backend.service;

import dbadmin.backend.dto.UserResponse;
import dbadmin.backend.entity.OperationType;
import dbadmin.backend.entity.Role;
import dbadmin.backend.entity.TargetType;
import dbadmin.backend.entity.User;
import dbadmin.backend.exception.ConflictException;
import dbadmin.backend.exception.NotFoundException;
import dbadmin.backend.exception.ValidationException;
import dbadmin.backend.repository.UserRepository;
import dbadmin.backend.security.UserRoleCacheService;
import dbadmin.backend.validation.NameValidator;
import java.util.List;
import java.util.Map;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Kullanici yonetiminin is mantigi (sadece ADMIN'in ulasabildigi uclarin arkasi).
 * Parolanin duz metinden hash'e cevrildigi tek yer burasidir — entity hash'i oldugu gibi
 * saklar, controller ise duz parolayi hic gormeden buraya gecirir.
 */
@Service
public class UserService {

    /** BCrypt'in kendi siniri 72 bayt; altindaki her sey kullaniciyi zorlamayan bir alt sinir. */
    private static final int MIN_PASSWORD_LENGTH = 8;

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final UserRoleCacheService userRoleCacheService;
    private final AuditLogService auditLogService;

    public UserService(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            UserRoleCacheService userRoleCacheService,
            AuditLogService auditLogService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.userRoleCacheService = userRoleCacheService;
        this.auditLogService = auditLogService;
    }

    /**
     * BILEREK {@code @Cacheable} YOK (once vardi, Pageable eklenirken kaldirildi — ayni
     * gerekce icin bkz. {@link TagService#listTags} javadoc'u): {@code Page<UserResponse>}
     * Redis'e YAZILABILIYOR ama {@code PageImpl}'in Jackson'in kullanabilecegi bir constructor'i
     * olmadigi icin geri OKUNAMIYOR ("Cannot construct instance of PageImpl (no Creators...)") —
     * CacheConfig'teki fail-open handler sayesinde hata firlamiyor ama her istek sessizce DB'ye
     * dusuyordu, yani cache hicbir sey kazandirmiyordu. Parola hash'inin Redis'e hic yazilmamasi
     * icin entity degil DTO ({@link UserResponse}) map'lenmesi karari (asagida) hala gecerli,
     * sadece {@code @Cacheable} kaldirildi.
     */
    @Transactional(readOnly = true)
    public Page<UserResponse> listUsers(Pageable pageable) {
        return userRepository.findAll(pageable).map(UserResponse::from);
    }

    @Transactional(readOnly = true)
    public User getUser(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new NotFoundException(
                        "NOT_FOUND_USER", "user not found: " + id, Map.of("id", String.valueOf(id))));
    }

    @Transactional(readOnly = true)
    public User getUserByUsername(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new NotFoundException(
                        "NOT_FOUND_USER", "user not found: " + username,
                        Map.of("id", String.valueOf(username))));
    }

    /** Rol verilmezse en dusuk yetkiyle (VIEWER) baslar — yeni kullanici kazara yazma yetkisi almasin. */
    @Transactional
    public User createUser(String username, String password, Role role) {
        NameValidator.validate("username", "VALIDATION_INVALID_USERNAME", username);
        verifyPassword(password);

        if (userRepository.existsByUsername(username)) {
            throw new ConflictException(
                    "CONFLICT_DUPLICATE_USERNAME",
                    "a user named '" + username + "' already exists",
                    Map.of("name", username));
        }

        Role assigned = role == null ? Role.VIEWER : role;
        User saved = userRepository.save(
                new User(username, passwordEncoder.encode(password), assigned));
        auditLogService.record(OperationType.USER_CREATED, TargetType.USER, saved.getId(),
                "kullanici olusturuldu: " + saved.getUsername() + " (rol=" + assigned + ")");
        return saved;
    }

    /**
     * Rol degistiginde cache'i EVICT ediyoruz (guncel degeri tekrar yazmak yerine silmek yeterli
     * — bir sonraki istek zaten cache miss'te DB'den taze rolu okuyup kendisi yazacak, bkz.
     * {@link dbadmin.backend.security.JwtAuthenticationFilter}). Bunu unutursak: kullanici eski
     * (yuksek) rolüyle TTL suresince (varsayilan 30 dk) islem yapmaya devam edebilir — bu yuzden
     * evict burada, DB degisikligiyle AYNI metodun icinde, unutulmasi zor bir yerde.
     */
    @Transactional
    public User changeRole(Long id, Role newRole) {
        if (newRole == null) {
            throw new ValidationException("VALIDATION_MISSING_ROLE", "a role must be specified");
        }
        User user = getUser(id);
        lastAdminGuard(user, newRole);
        Role oldRole = user.getRole();
        user.setRole(newRole);
        userRoleCacheService.evict(user.getUsername());
        if (oldRole != newRole) {
            auditLogService.record(OperationType.USER_ROLE_CHANGED, TargetType.USER, id,
                    "rol degisti: " + oldRole + " -> " + newRole + " (kullanici=" + user.getUsername() + ")");
        }
        return user;
    }

    @Transactional
    public void deleteUser(Long id) {
        User user = getUser(id);
        lastAdminGuard(user, null);
        String username = user.getUsername();
        userRepository.delete(user);
        userRoleCacheService.evict(username);
        auditLogService.record(OperationType.USER_DELETED, TargetType.USER, id,
                "kullanici silindi: " + username);
    }

    /**
     * Sistemde en az bir ADMIN kalmasini garanti eder. Bu kontrol olmasaydi tek admin kendi
     * rolunu dusurup ya da kendini silip <b>kullanici yonetimine bir daha girilemez</b> hale
     * getirebilirdi — geri donusu sadece veritabanina elle mudahaleyle olan bir kilitlenme.
     *
     * @param newRole rol degisikligi icin yeni rol; silme isleminde {@code null}
     */
    private void lastAdminGuard(User user, Role newRole) {
        boolean leavingAdmin = user.getRole() == Role.ADMIN && newRole != Role.ADMIN;
        if (!leavingAdmin) {
            return;
        }
        long adminCount = userRepository.findAll().stream()
                .filter(u -> u.getRole() == Role.ADMIN)
                .count();
        if (adminCount <= 1) {
            throw new ConflictException(
                    "CONFLICT_LAST_ADMIN", "the last remaining admin cannot be removed or demoted");
        }
    }

    private void verifyPassword(String password) {
        if (password == null || password.length() < MIN_PASSWORD_LENGTH) {
            throw new ValidationException(
                    "VALIDATION_WEAK_PASSWORD",
                    "password must be at least " + MIN_PASSWORD_LENGTH + " characters",
                    Map.of("min", String.valueOf(MIN_PASSWORD_LENGTH)));
        }
    }
}

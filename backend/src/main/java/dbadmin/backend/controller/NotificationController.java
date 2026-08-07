package dbadmin.backend.controller;

import dbadmin.backend.dto.NotificationResponse;
import dbadmin.backend.dto.UnreadCountResponse;
import dbadmin.backend.entity.User;
import dbadmin.backend.service.NotificationService;
import dbadmin.backend.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Bildirim merkezinin okuma/okundu-isaretleme uclari — bkz.
 * requirement-websocket-notifications.md Req-2.6/Req-2.7. Diger "sadece ADMIN" uclarinin
 * ({@code /api/audit-logs/**}, {@code /api/users/**}) aksine burada rol kisiti YOK
 * (Req-3.6): herhangi bir kimlikli kullanici erisebilir, filtre rol bazli degil {@link
 * NotificationService}'in her sorguda uyguladigi "sadece kendi recipientUserId'si" kuralidir —
 * bkz. SecurityConfig'teki {@code /api/notifications/**} kurali.
 */
@RestController
@RequestMapping("/api/notifications")
@Tag(name = "Bildirimler", description = "Tablo sahipligi temelli, hedefli bildirimler (herkes kendi bildirimini gorur)")
public class NotificationController {

    private final NotificationService notificationService;
    private final UserService userService;

    public NotificationController(NotificationService notificationService, UserService userService) {
        this.notificationService = notificationService;
        this.userService = userService;
    }

    private Long currentUserId(Authentication authentication) {
        User user = userService.getUserByUsername(authentication.getName());
        return user.getId();
    }

    @Operation(summary = "Kendi bildirimlerini sayfali listeler", description = "En yeni once. "
            + "Sadece istegi yapan kullanicinin kendi bildirimleri donulur.")
    @GetMapping
    public Page<NotificationResponse> list(Authentication authentication, Pageable pageable) {
        return notificationService.list(currentUserId(authentication), pageable).map(NotificationResponse::from);
    }

    @Operation(summary = "Okunmamis bildirim sayisi", description = "Zil ikonundaki sayac icin ilk "
            + "(ve tek) sunucu sorgusu — bundan sonra sayac WebSocket push'uyla yerelde guncellenir.")
    @GetMapping("/unread-count")
    public UnreadCountResponse unreadCount(Authentication authentication) {
        return new UnreadCountResponse(notificationService.unreadCount(currentUserId(authentication)));
    }

    @Operation(summary = "Tekil bildirimi okundu isaretle", description = "Baska bir kullanicinin "
            + "bildirimi 404 doner (var/yok bilgisi disariya sizdirilmaz).")
    @PatchMapping("/{id}/read")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void markAsRead(@PathVariable Long id, Authentication authentication) {
        notificationService.markAsRead(id, currentUserId(authentication));
    }

    @Operation(summary = "Tum bildirimleri okundu isaretle", description = "Sadece istegi yapan "
            + "kullanicinin kendi bildirimleri etkilenir.")
    @PatchMapping("/read")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void markAllAsRead(Authentication authentication) {
        notificationService.markAllAsRead(currentUserId(authentication));
    }
}

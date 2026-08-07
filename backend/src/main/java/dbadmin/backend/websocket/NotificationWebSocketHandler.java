package dbadmin.backend.websocket;

import dbadmin.backend.entity.User;
import dbadmin.backend.repository.UserRepository;
import dbadmin.backend.security.JwtService;
import dbadmin.backend.security.UserRoleCacheService;
import io.jsonwebtoken.JwtException;
import java.net.URI;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * {@code /ws} baglantisinin kimlik dogrulamasini yapan ve acik session'i {@link
 * WebSocketSessionRegistry}'ye kaydeden handler — bkz. requirement-websocket-notifications.md
 * Faz 3.
 * <p>
 * Neden STOMP/SockJS degil de ham {@link TextWebSocketHandler}: mekanizmanin (handshake'te JWT
 * dogrulama, kullaniciId -> session eslemesi) gorunur kalmasi bilinclii bir tercih — projenin
 * JWT/Redis'i de elle yazma tercihiyle ayni cizgide (bkz. plan-websocket-notifications-implementation.md).
 * <p>
 * Neden JWT query param'da (Req-3.1): tarayicinin native {@code WebSocket} API'si handshake
 * istegine custom header ekleyemiyor (JS'te sadece URL veriliyor); yani token'i {@code Authorization}
 * basligina koymanin bir yolu yok. Bilinen ve kabul edilen bedeli: query param'lar access log'lara
 * ve ara proxy'lere sizabilir — bu projede kisa omurlu JWT + ogrenme ortami oldugu icin kabul
 * edilebilir bir trade-off.
 */
@Component
public class NotificationWebSocketHandler extends TextWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(NotificationWebSocketHandler.class);

    /** Session.getAttributes() icinde kullaniciId'yi tasimak icin kullanilan anahtar. */
    static final String ATTR_USER_ID = "userId";

    private final JwtService jwtService;
    private final UserRoleCacheService userRoleCacheService;
    private final UserRepository userRepository;
    private final WebSocketSessionRegistry registry;

    public NotificationWebSocketHandler(
            JwtService jwtService,
            UserRoleCacheService userRoleCacheService,
            UserRepository userRepository,
            WebSocketSessionRegistry registry) {
        this.jwtService = jwtService;
        this.userRoleCacheService = userRoleCacheService;
        this.userRepository = userRepository;
        this.registry = registry;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        String token = extractToken(session.getUri());
        if (token == null) {
            close(session, "token eksik");
            return;
        }
        String username;
        try {
            username = jwtService.extractUsername(token);
        } catch (JwtException | IllegalArgumentException ex) {
            close(session, "gecersiz token");
            return;
        }
        Long userId = resolveUserId(username);
        if (userId == null) {
            close(session, "kullanici bulunamadi");
            return;
        }
        session.getAttributes().put(ATTR_USER_ID, userId);
        registry.add(userId, session);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        Object userId = session.getAttributes().get(ATTR_USER_ID);
        if (userId instanceof Long id) {
            registry.remove(id, session);
        }
    }

    /** Bu kanal sadece server -> client push icin: istemciden gelen metin mesajlarinin bir karsiligi yok, sessizce yok sayilir. */

    private String extractToken(URI uri) {
        if (uri == null) {
            return null;
        }
        return UriComponentsBuilder.fromUri(uri).build().getQueryParams().getFirst("token");
    }

    private Long resolveUserId(String username) {
        return userRoleCacheService.get(username)
                .map(UserRoleCacheService.CachedUser::id)
                .orElseGet(() -> userRepository.findByUsername(username)
                        .map(User::getId)
                        .orElse(null));
    }

    private void close(WebSocketSession session, String reason) {
        try {
            log.debug("WebSocket handshake reddedildi: {}", reason);
            session.close(CloseStatus.POLICY_VIOLATION.withReason(reason));
        } catch (Exception ex) {
            log.warn("WebSocket session kapatilirken hata: {}", ex.getMessage());
        }
    }
}

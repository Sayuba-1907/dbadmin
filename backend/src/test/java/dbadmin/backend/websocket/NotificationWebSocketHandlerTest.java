package dbadmin.backend.websocket;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dbadmin.backend.entity.User;
import dbadmin.backend.repository.UserRepository;
import dbadmin.backend.security.JwtService;
import dbadmin.backend.security.UserRoleCacheService;
import io.jsonwebtoken.JwtException;
import java.net.URI;
import java.util.HashMap;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;

/**
 * Sadece {@link NotificationWebSocketHandler}'in handshake kararlarini test eder (gercek DB/Redis
 * gerekmez) — bkz. requirement-websocket-notifications.md Req-3.1. {@link JwtService},
 * {@link UserRoleCacheService}, {@link UserRepository}, {@link WebSocketSessionRegistry}
 * hepsi mock: burada dogrulanan sey bu 4 bagimliligin "gecerli token -> kayit", "gecersiz/eksik
 * token -> kapat" akisinda dogru sirayla cagrilmasidir.
 */
class NotificationWebSocketHandlerTest {

    private JwtService jwtService;
    private UserRoleCacheService userRoleCacheService;
    private UserRepository userRepository;
    private WebSocketSessionRegistry registry;
    private NotificationWebSocketHandler handler;

    @BeforeEach
    void setUp() {
        jwtService = mock(JwtService.class);
        userRoleCacheService = mock(UserRoleCacheService.class);
        userRepository = mock(UserRepository.class);
        registry = mock(WebSocketSessionRegistry.class);
        handler = new NotificationWebSocketHandler(jwtService, userRoleCacheService, userRepository, registry);
    }

    private WebSocketSession mockSession(String query) {
        WebSocketSession session = mock(WebSocketSession.class);
        when(session.getUri()).thenReturn(URI.create("ws://localhost/ws" + (query != null ? "?" + query : "")));
        when(session.getAttributes()).thenReturn(new HashMap<>());
        return session;
    }

    @Test
    void afterConnectionEstablished_gecerliToken_sessionRegistryyeEklenir() throws Exception {
        WebSocketSession session = mockSession("token=gecerli-token");
        when(jwtService.extractUsername("gecerli-token")).thenReturn("editor1");
        when(userRoleCacheService.get("editor1")).thenReturn(Optional.empty());
        User user = mock(User.class);
        when(user.getId()).thenReturn(42L);
        when(userRepository.findByUsername("editor1")).thenReturn(Optional.of(user));

        handler.afterConnectionEstablished(session);

        verify(registry).add(eq(42L), eq(session));
        assertEquals(42L, session.getAttributes().get(NotificationWebSocketHandler.ATTR_USER_ID));
        verify(session, never()).close(any());
    }

    @Test
    void afterConnectionEstablished_tokenYok_baglantiKapatilir() throws Exception {
        WebSocketSession session = mockSession(null);

        handler.afterConnectionEstablished(session);

        verify(registry, never()).add(any(), any());
        verify(session).close(any(CloseStatus.class));
    }

    @Test
    void afterConnectionEstablished_gecersizToken_baglantiKapatilir() throws Exception {
        WebSocketSession session = mockSession("token=bozuk");
        when(jwtService.extractUsername("bozuk")).thenThrow(new JwtException("imza tutmuyor"));

        handler.afterConnectionEstablished(session);

        verify(registry, never()).add(any(), any());
        verify(session).close(any(CloseStatus.class));
    }

    @Test
    void afterConnectionEstablished_kullaniciBulunamadi_baglantiKapatilir() throws Exception {
        WebSocketSession session = mockSession("token=gecerli-ama-user-silinmis");
        when(jwtService.extractUsername(anyString())).thenReturn("silinmis_kullanici");
        when(userRoleCacheService.get("silinmis_kullanici")).thenReturn(Optional.empty());
        when(userRepository.findByUsername("silinmis_kullanici")).thenReturn(Optional.empty());

        handler.afterConnectionEstablished(session);

        verify(registry, never()).add(any(), any());
        verify(session).close(any(CloseStatus.class));
    }

    @Test
    void afterConnectionClosed_registrydenCikarilir() {
        WebSocketSession session = mock(WebSocketSession.class);
        HashMap<String, Object> attrs = new HashMap<>();
        attrs.put(NotificationWebSocketHandler.ATTR_USER_ID, 42L);
        when(session.getAttributes()).thenReturn(attrs);

        handler.afterConnectionClosed(session, CloseStatus.NORMAL);

        verify(registry).remove(42L, session);
    }
}

package dbadmin.backend.websocket;

import java.io.IOException;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

/**
 * {@code userId -> acik WebSocket session'lari} eslemesi — bkz.
 * requirement-websocket-notifications.md Req-3.1. Bir kullanici birden fazla sekme/cihazdan
 * bagli olabilecegi icin deger tek bir session degil bir {@link Set}.
 * <p>
 * {@link #send} best-effort'tur (Req-3.4): hedef kullanicinin bagli bir session'i yoksa ya da
 * gonderim sirasinda hata olursa sessizce atlanir, hicbir exception cagirana sizmaz — anlik
 * bildirim bir garanti degil bir optimizasyondur, kalici kaydi ({@code Notification} satiri) zaten
 * garantilidir.
 */
@Component
public class WebSocketSessionRegistry {

    private static final Logger log = LoggerFactory.getLogger(WebSocketSessionRegistry.class);

    private final ConcurrentHashMap<Long, Set<WebSocketSession>> sessions = new ConcurrentHashMap<>();

    public void add(Long userId, WebSocketSession session) {
        sessions.computeIfAbsent(userId, id -> ConcurrentHashMap.newKeySet()).add(session);
    }

    /** Session kapaninca (bkz. {@code NotificationWebSocketHandler#afterConnectionClosed}) hayalet kayit kalmasin diye cagirilir. */
    public void remove(Long userId, WebSocketSession session) {
        sessions.computeIfPresent(userId, (id, set) -> {
            set.remove(session);
            return set.isEmpty() ? null : set;
        });
    }

    /** Kullanicinin (varsa) tum acik session'larina ayni mesaji yollar; hicbiri bagli degilse no-op. */
    public void send(Long userId, String messageJson) {
        Set<WebSocketSession> targetSessions = sessions.get(userId);
        if (targetSessions == null || targetSessions.isEmpty()) {
            return;
        }
        TextMessage message = new TextMessage(messageJson);
        for (WebSocketSession session : targetSessions) {
            try {
                if (session.isOpen()) {
                    session.sendMessage(message);
                }
            } catch (IOException ex) {
                // Fail-open push (Req-3.4): tek bir session'a yazamamak digerlerini ya da
                // kalici bildirim kaydini etkilememeli, sadece loglanir.
                log.warn("Bildirim push edilemedi (userId={}): {}", userId, ex.getMessage());
            }
        }
    }
}

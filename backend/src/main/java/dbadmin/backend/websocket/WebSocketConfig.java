package dbadmin.backend.websocket;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

/**
 * {@code /ws} ucunu {@link NotificationWebSocketHandler}'a baglar. STOMP/SockJS kullanilmiyor —
 * bkz. {@link NotificationWebSocketHandler} javadoc'undaki gerekce.
 * <p>
 * {@code setAllowedOrigins("*")}: bu proje CORS'u zaten {@code CorsConfigurationSource} ile
 * REST tarafinda kontrol ediyor (bkz. SecurityConfig); WebSocket handshake'i ayni origin
 * kisitina tabi degil ve gercek kimlik dogrulamasi origin'e degil {@link NotificationWebSocketHandler}
 * icindeki JWT kontrolune dayaniyor.
 */
@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final NotificationWebSocketHandler notificationWebSocketHandler;

    public WebSocketConfig(NotificationWebSocketHandler notificationWebSocketHandler) {
        this.notificationWebSocketHandler = notificationWebSocketHandler;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(notificationWebSocketHandler, "/ws").setAllowedOrigins("*");
    }
}

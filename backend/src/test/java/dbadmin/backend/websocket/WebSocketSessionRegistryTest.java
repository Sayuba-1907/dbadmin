package dbadmin.backend.websocket;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import org.junit.jupiter.api.Test;
import org.springframework.web.socket.WebSocketSession;

/** Saf birim testi (Spring context yok): {@link WebSocketSessionRegistry}'nin best-effort davranisi (Req-3.4). */
class WebSocketSessionRegistryTest {

    private final WebSocketSessionRegistry registry = new WebSocketSessionRegistry();

    @Test
    void gonder_kayitliSessionaMesajYollar() throws IOException {
        WebSocketSession session = mock(WebSocketSession.class);
        when(session.isOpen()).thenReturn(true);
        registry.add(1L, session);

        registry.send(1L, "{\"test\":true}");

        verify(session).sendMessage(any());
    }

    @Test
    void gonder_kayitliOlmayanKullaniciIcin_sessizceNoOp() {
        // Hicbir session eklenmedi; exception firlamamali (Req-3.4 fail-open).
        registry.send(999L, "{\"test\":true}");
    }

    @Test
    void gonder_kapaliSessiona_mesajYollamaz() throws IOException {
        WebSocketSession session = mock(WebSocketSession.class);
        when(session.isOpen()).thenReturn(false);
        registry.add(2L, session);

        registry.send(2L, "{\"test\":true}");

        verify(session, never()).sendMessage(any());
    }

    @Test
    void cikar_sonraGonderilenMesajArtikUlasmaz() throws IOException {
        WebSocketSession session = mock(WebSocketSession.class);
        when(session.isOpen()).thenReturn(true);
        registry.add(3L, session);
        registry.remove(3L, session);

        registry.send(3L, "{\"test\":true}");

        verify(session, never()).sendMessage(any());
    }

    @Test
    void gonder_sendMessageHataFirlatirsa_exceptionYutulur() throws IOException {
        WebSocketSession session = mock(WebSocketSession.class);
        when(session.isOpen()).thenReturn(true);
        org.mockito.Mockito.doThrow(new IOException("baglanti koptu")).when(session).sendMessage(any());
        registry.add(4L, session);

        // Exception disariya sizmamali (fail-open).
        registry.send(4L, "{\"test\":true}");
    }
}

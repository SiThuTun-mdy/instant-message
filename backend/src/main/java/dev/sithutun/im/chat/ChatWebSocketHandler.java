package dev.sithutun.im.chat;

import java.io.IOException;
import java.time.Instant;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

/**
 * The whole chat protocol, as JSON text frames.
 *
 * <pre>
 * client -> server  {"type":"send","clientMessageId":"uuid","to":"bob","body":"hi"}
 * server -> sender  {"type":"ack","clientMessageId":"uuid","sentAt":"..."}
 * server -> peer    {"type":"message","id":"uuid","from":"alice","to":"bob","body":"hi","sentAt":"..."}
 * server -> sender  {"type":"error","clientMessageId":"uuid","code":"recipient_not_found"}
 * </pre>
 */
@Component
public class ChatWebSocketHandler extends TextWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(ChatWebSocketHandler.class);

    record SendFrame(String type, UUID clientMessageId, String to, String body) {
    }

    record AckFrame(String type, UUID clientMessageId, Instant sentAt) {
    }

    record MessageFrame(String type, UUID id, String from, String to, String body, Instant sentAt) {
    }

    record ErrorFrame(String type, UUID clientMessageId, String code) {
    }

    private final ChatService chatService;
    private final SessionRegistry registry;
    private final ObjectMapper objectMapper;

    public ChatWebSocketHandler(ChatService chatService, SessionRegistry registry, ObjectMapper objectMapper) {
        this.chatService = chatService;
        this.registry = registry;
        this.objectMapper = objectMapper;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        registry.register(userId(session), session);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        registry.unregister(userId(session), session.getId());
    }

    @Override
    protected void handleTextMessage(WebSocketSession rawSession, TextMessage message) {
        UUID senderId = userId(rawSession);
        WebSocketSession session = registry.find(senderId, rawSession.getId());
        if (session == null) {
            return;
        }

        SendFrame frame;
        try {
            frame = objectMapper.readValue(message.getPayload(), SendFrame.class);
        } catch (JacksonException e) {
            send(session, new ErrorFrame("error", null, "bad_request"));
            return;
        }
        if (frame == null || !"send".equals(frame.type())) {
            send(session, new ErrorFrame("error", frame == null ? null : frame.clientMessageId(), "bad_request"));
            return;
        }

        ChatService.Sent sent;
        try {
            String senderName = (String) rawSession.getAttributes().get(JwtHandshakeInterceptor.ACCOUNT_NAME);
            sent = chatService.send(senderId, senderName, frame.clientMessageId(), frame.to(), frame.body());
        } catch (ChatException e) {
            send(session, new ErrorFrame("error", frame.clientMessageId(), e.getCode()));
            return;
        } catch (RuntimeException e) {
            log.error("Failed to store message {}", frame.clientMessageId(), e);
            send(session, new ErrorFrame("error", frame.clientMessageId(), "server_error"));
            return;
        }

        // The message is stored at this point, so the ack is safe even if the push below fails:
        // the recipient will get it from history.
        MessageDto m = sent.message();
        send(session, new AckFrame("ack", m.id(), m.sentAt()));
        if (!sent.duplicate()) {
            MessageFrame push = new MessageFrame("message", m.id(), m.from(), m.to(), m.body(), m.sentAt());
            registry.sessionsOf(sent.recipientId()).forEach(s -> send(s, push));
        }
    }

    private void send(WebSocketSession session, Object frame) {
        try {
            session.sendMessage(new TextMessage(objectMapper.writeValueAsString(frame)));
        } catch (IOException | RuntimeException e) {
            log.debug("Could not send to session {}: {}", session.getId(), e.getMessage());
        }
    }

    private static UUID userId(WebSocketSession session) {
        return (UUID) session.getAttributes().get(JwtHandshakeInterceptor.USER_ID);
    }
}

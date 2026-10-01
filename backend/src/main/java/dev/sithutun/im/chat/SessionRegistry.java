package dev.sithutun.im.chat;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator;

/** Open WebSocket sessions per user; a user has one per browser tab or device. */
@Component
public class SessionRegistry {

    private static final int SEND_TIME_LIMIT_MS = 5_000;
    private static final int BUFFER_SIZE_LIMIT_BYTES = 512 * 1024;

    private final Map<UUID, Map<String, WebSocketSession>> sessionsByUser = new ConcurrentHashMap<>();

    /**
     * A raw session does not allow concurrent sends, and a reply to the session's own frame can
     * race with a push from another user's thread, so every send must go through the returned
     * decorator.
     */
    public WebSocketSession register(UUID userId, WebSocketSession session) {
        WebSocketSession safe =
                new ConcurrentWebSocketSessionDecorator(session, SEND_TIME_LIMIT_MS, BUFFER_SIZE_LIMIT_BYTES);
        sessionsByUser.computeIfAbsent(userId, id -> new ConcurrentHashMap<>()).put(session.getId(), safe);
        return safe;
    }

    public void unregister(UUID userId, String sessionId) {
        sessionsByUser.computeIfPresent(userId, (id, sessions) -> {
            sessions.remove(sessionId);
            return sessions.isEmpty() ? null : sessions;
        });
    }

    public WebSocketSession find(UUID userId, String sessionId) {
        return sessionsByUser.getOrDefault(userId, Map.of()).get(sessionId);
    }

    public Collection<WebSocketSession> sessionsOf(UUID userId) {
        return List.copyOf(sessionsByUser.getOrDefault(userId, Map.of()).values());
    }
}

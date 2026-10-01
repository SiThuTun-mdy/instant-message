package dev.sithutun.im.chat;

import dev.sithutun.im.auth.JwtService;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;
import org.springframework.web.util.UriComponentsBuilder;

/** Authenticates the WebSocket handshake from the {@code token} query parameter. */
@Component
public class JwtHandshakeInterceptor implements HandshakeInterceptor {

    static final String USER_ID = "userId";
    static final String ACCOUNT_NAME = "accountName";

    private final JwtDecoder jwtDecoder;

    public JwtHandshakeInterceptor(JwtDecoder jwtDecoder) {
        this.jwtDecoder = jwtDecoder;
    }

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
            WebSocketHandler wsHandler, Map<String, Object> attributes) {
        String token = UriComponentsBuilder.fromUri(request.getURI()).build().getQueryParams().getFirst("token");
        try {
            if (token == null) {
                throw new JwtException("Missing token");
            }
            Jwt jwt = jwtDecoder.decode(token);
            attributes.put(USER_ID, UUID.fromString(jwt.getSubject()));
            attributes.put(ACCOUNT_NAME, jwt.getClaimAsString(JwtService.ACCOUNT_NAME_CLAIM));
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            return false;
        }
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
            WebSocketHandler wsHandler, Exception exception) {
    }
}

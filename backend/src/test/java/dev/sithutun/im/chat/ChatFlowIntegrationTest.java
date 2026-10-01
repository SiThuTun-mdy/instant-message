package dev.sithutun.im.chat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.web.client.RestClient;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class ChatFlowIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17");

    private static final JsonMapper JSON = new JsonMapper();

    @Value("${local.server.port}")
    int port;

    @Test
    void messageIsStoredAckedPushedAndDeduplicated() throws Exception {
        String aliceToken = signupAndLogin("alice");
        String bobToken = signupAndLogin("bob");
        Client alice = connect(aliceToken);
        Client bob = connect(bobToken);

        UUID id = UUID.randomUUID();
        String send = JSON.writeValueAsString(
                Map.of("type", "send", "clientMessageId", id.toString(), "to", "bob", "body", "hello bob"));
        alice.session.sendMessage(new TextMessage(send));

        JsonNode ack = alice.next();
        assertThat(ack.get("type").asString()).isEqualTo("ack");
        assertThat(ack.get("clientMessageId").asString()).isEqualTo(id.toString());

        JsonNode pushed = bob.next();
        assertThat(pushed.get("type").asString()).isEqualTo("message");
        assertThat(pushed.get("id").asString()).isEqualTo(id.toString());
        assertThat(pushed.get("from").asString()).isEqualTo("alice");
        assertThat(pushed.get("body").asString()).isEqualTo("hello bob");

        // A retry with the same id is acked again but neither stored nor pushed twice
        alice.session.sendMessage(new TextMessage(send));
        assertThat(alice.next().get("type").asString()).isEqualTo("ack");
        assertThat(bob.frames.poll(500, TimeUnit.MILLISECONDS)).isNull();

        List<Map<String, Object>> history = http().get()
                .uri("/api/v1/conversations/alice/messages")
                .header("Authorization", "Bearer " + bobToken)
                .retrieve()
                .body(new ParameterizedTypeReference<>() {
                });
        assertThat(history).hasSize(1);
        assertThat(history.get(0)).containsEntry("id", id.toString()).containsEntry("from", "alice");
        assertThat(history.get(0).get("sentAt")).isEqualTo(ack.get("sentAt").asString());

        List<Map<String, Object>> peers = http().get()
                .uri("/api/v1/conversations")
                .header("Authorization", "Bearer " + bobToken)
                .retrieve()
                .body(new ParameterizedTypeReference<>() {
                });
        assertThat(peers).extracting(p -> p.get("accountName")).containsExactly("alice");

        alice.session.sendMessage(new TextMessage(JSON.writeValueAsString(
                Map.of("type", "send", "clientMessageId", UUID.randomUUID().toString(), "to", "nobody", "body", "x"))));
        JsonNode error = alice.next();
        assertThat(error.get("type").asString()).isEqualTo("error");
        assertThat(error.get("code").asString()).isEqualTo("recipient_not_found");

        alice.session.close();
        bob.session.close();
    }

    @Test
    void handshakeWithoutValidTokenIsRejected() {
        assertThatThrownBy(() -> connect("not-a-jwt")).isInstanceOf(Exception.class);
    }

    private RestClient http() {
        return RestClient.create("http://localhost:" + port);
    }

    private String signupAndLogin(String name) {
        http().post().uri("/api/v1/auth/signup")
                .body(Map.of("email", name + "@gmail.com", "accountName", name,
                        "displayName", name, "password", "password123"))
                .retrieve().toBodilessEntity();
        Map<String, Object> login = http().post().uri("/api/v1/auth/login")
                .body(Map.of("identifier", name, "password", "password123"))
                .retrieve().body(new ParameterizedTypeReference<>() {
                });
        return (String) login.get("accessToken");
    }

    private Client connect(String token) throws Exception {
        Client client = new Client();
        client.session = new StandardWebSocketClient()
                .execute(client, new WebSocketHttpHeaders(), URI.create("ws://localhost:" + port + "/ws?token=" + token))
                .get(5, TimeUnit.SECONDS);
        return client;
    }

    private static class Client extends TextWebSocketHandler {

        final BlockingQueue<String> frames = new LinkedBlockingQueue<>();
        WebSocketSession session;

        @Override
        protected void handleTextMessage(WebSocketSession session, TextMessage message) {
            frames.add(message.getPayload());
        }

        JsonNode next() throws Exception {
            String frame = frames.poll(5, TimeUnit.SECONDS);
            assertThat(frame).as("expected a frame within 5s").isNotNull();
            return JSON.readTree(frame);
        }
    }
}

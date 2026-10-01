package dev.sithutun.im.chat;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import org.springframework.web.socket.server.standard.ServletServerContainerFactoryBean;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final ChatWebSocketHandler handler;
    private final JwtHandshakeInterceptor interceptor;
    private final String frontendOrigin;

    public WebSocketConfig(ChatWebSocketHandler handler, JwtHandshakeInterceptor interceptor,
            @Value("${app.frontend-origin}") String frontendOrigin) {
        this.handler = handler;
        this.interceptor = interceptor;
        this.frontendOrigin = frontendOrigin;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(handler, "/ws")
                .addInterceptors(interceptor)
                .setAllowedOrigins(frontendOrigin);
    }

    @Bean
    ServletServerContainerFactoryBean webSocketContainer() {
        ServletServerContainerFactoryBean container = new ServletServerContainerFactoryBean();
        // Room for a 2000-character body in any script, plus the JSON envelope
        container.setMaxTextMessageBufferSize(16 * 1024);
        return container;
    }
}

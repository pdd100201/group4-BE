package com.onlinelearning.config;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.web.socket.config.annotation.*;
import java.util.Arrays;

@Configuration @EnableWebSocket @EnableScheduling @RequiredArgsConstructor
public class ChatWebSocketConfig implements WebSocketConfigurer {
    private final ChatSocketHandler handler;
    @Value("${app.cors.allowed-origins}") private String origins;
    @Override public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(handler, "/ws/chat")
            .setAllowedOrigins(Arrays.stream(origins.split(",")).map(String::trim).toArray(String[]::new));
    }
}

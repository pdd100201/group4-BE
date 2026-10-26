package com.onlinelearning.config;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.onlinelearning.dto.chat.ChatMessageResponse;
import com.onlinelearning.exception.ApiException;
import com.onlinelearning.service.ChatIdentityService;
import com.onlinelearning.service.ChatService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.*;
import org.springframework.web.socket.handler.*;
import java.io.IOException;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component @RequiredArgsConstructor
public class ChatSocketHandler extends TextWebSocketHandler {
    private final ObjectMapper json;
    private final ChatIdentityService identity;
    private final ChatService chat;
    private final Map<String, Client> clients = new ConcurrentHashMap<>();

    public record Command(String type, String token, Long recipientId, String content, String clientMessageId, Long attachmentId) {}
    private static final class Client {
        final WebSocketSession socket;
        final Instant connected = Instant.now();
        volatile String token;
        volatile Long userId;
        long windowStart = System.currentTimeMillis();
        int count;
        Client(WebSocketSession socket) { this.socket = socket; }
        synchronized boolean allow() {
            long now = System.currentTimeMillis();
            if (now - windowStart >= 10_000) { windowStart = now; count = 0; }
            return ++count <= 30;
        }
    }

    @Override public void afterConnectionEstablished(WebSocketSession session) {
        session.setTextMessageSizeLimit(16_384);
        clients.put(session.getId(), new Client(new ConcurrentWebSocketSessionDecorator(session, 5000, 65_536)));
    }

    @Override protected void handleTextMessage(WebSocketSession session, TextMessage message) throws IOException {
        Client client = clients.get(session.getId());
        if (client == null) return;
        Command command = null;
        try {
            if (!client.allow()) { write(client, Map.of("type", "ERROR", "message", "Bạn gửi quá nhanh. Vui lòng chờ vài giây.")); return; }
            command = json.readValue(message.getPayload(), Command.class);
            if (command == null) throw new IllegalArgumentException();
            if ("AUTH".equals(command.type())) {
                if (client.userId != null) { session.close(CloseStatus.POLICY_VIOLATION); return; }
                Long userId = identity.tokenUser(command.token());
                client.token = command.token(); client.userId = userId;
                write(client, Map.of("type", "READY", "userId", userId));
                presence();
                return;
            }
            if (client.userId == null) { session.close(CloseStatus.POLICY_VIOLATION); return; }
            identity.tokenUser(client.token);
            if ("PING".equals(command.type())) { write(client, Map.of("type", "PONG")); return; }
            if (!"SEND".equals(command.type())) throw new IllegalArgumentException();
            // Service transaction has committed before any socket receives the message.
            ChatMessageResponse saved = chat.send(client.userId, command.recipientId(), command.content(), command.clientMessageId(), command.attachmentId());
            broadcast(saved);
        } catch (ApiException ex) {
            if (ex.status().value() == 401) { session.close(CloseStatus.POLICY_VIOLATION); return; }
            error(client, command, ex.getMessage());
        } catch (JsonProcessingException | IllegalArgumentException ex) {
            error(client, command, "Dữ liệu tin nhắn không hợp lệ.");
        } catch (RuntimeException ex) {
            // Do not return SQL details, tokens or stack traces to a socket client.
            error(client, command, "Chưa gửi được tin nhắn. Vui lòng thử lại.");
        }
    }

    private void broadcast(ChatMessageResponse message) {
        for (Client client : clients.values()) {
            if (client.userId == null || (message.recipientId() != null &&
                !client.userId.equals(message.sender().id()) && !client.userId.equals(message.recipientId()))) continue;
            try {
                identity.tokenUser(client.token);
                write(client, Map.of("type", "MESSAGE", "message", message));
            } catch (RuntimeException | IOException ex) { disconnect(client); }
        }
    }

    private void error(Client client, Command command, String message) throws IOException {
        write(client, Map.of("type", "ERROR", "message", message,
            "clientMessageId", command == null || command.clientMessageId() == null ? "" : command.clientMessageId()));
    }
    private void write(Client client, Object data) throws IOException {
        if (client.socket.isOpen()) client.socket.sendMessage(new TextMessage(json.writeValueAsString(data)));
    }
    private void disconnect(Client client) {
        clients.remove(client.socket.getId());
        try { client.socket.close(CloseStatus.POLICY_VIOLATION); } catch (IOException ignored) { }
    }

    private void presence() {
        var ids = clients.values().stream().map(c -> c.userId).filter(java.util.Objects::nonNull).distinct().toList();
        for (Client c : clients.values()) if (c.userId != null) {
            try { identity.tokenUser(c.token); write(c, Map.of("type", "PRESENCE", "userIds", ids)); } catch (IOException | RuntimeException ex) { disconnect(c); }
        }
    }

    @Scheduled(fixedDelay = 30_000)
    public void expireSessions() {
        for (Client client : clients.values()) {
            if (client.userId == null) {
                if (client.connected.plusSeconds(10).isBefore(Instant.now())) disconnect(client);
            } else {
                try { identity.tokenUser(client.token); } catch (RuntimeException ex) { disconnect(client); }
            }
        }
        presence();
    }
    @Override public void afterConnectionClosed(WebSocketSession session, CloseStatus status) { clients.remove(session.getId()); presence(); }
    @Override public void handleTransportError(WebSocketSession session, Throwable exception) throws IOException {
        clients.remove(session.getId()); session.close(CloseStatus.SERVER_ERROR);
    }
}

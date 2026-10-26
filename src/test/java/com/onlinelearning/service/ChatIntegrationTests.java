package com.onlinelearning.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.onlinelearning.config.JwtTokenProvider;
import com.onlinelearning.api.ChatMediaStorage;
import com.onlinelearning.api.MediaStorage.StoredMedia;
import com.onlinelearning.entity.*;
import com.onlinelearning.repository.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.core.io.ByteArrayResource;
import java.nio.file.*;
import java.net.URI;
import java.net.http.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Real HTTP security, JPA transactions and WebSocket frames. Only isolated in-memory H2 is used. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {"spring.datasource.url=jdbc:h2:mem:chat_test;MODE=MySQL;DB_CLOSE_DELAY=-1", "ngrok.enabled=false", "app.chat.storage-dir=target/test-chat-attachments"})
@AutoConfigureMockMvc @ActiveProfiles("test")
class ChatIntegrationTests {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired UserRepository users;
    @Autowired RoleRepository roles;
    @Autowired AuthSessionRepository sessions;
    @Autowired ChatMessageRepository messages;
    @Autowired JwtTokenProvider jwt;
    @Autowired ChatService chat;
    @MockitoBean ChatMediaStorage storage;
    @Autowired ChatAttachmentRepository attachments;
    @LocalServerPort int port;
    record Actor(User user, AuthSession session, String token) { }
    private final Map<String, byte[]> cloudFiles = new ConcurrentHashMap<>();

    @BeforeEach void privateCloudinaryFake() {
        cloudFiles.clear();
        when(storage.upload(any(Path.class), anyString(), anyBoolean())).thenAnswer(call -> {
            String id = "chat-test/" + UUID.randomUUID(); byte[] bytes = Files.readAllBytes(call.getArgument(0));
            cloudFiles.put(id, bytes); boolean video = call.getArgument(2);
            return new StoredMedia(id, video ? "video" : "image", "authenticated", video ? "mp4" : "png", "https://cloudinary.invalid/private", bytes.length);
        });
        when(storage.download(anyString(), anyString(), anyString())).thenAnswer(call -> new ByteArrayResource(cloudFiles.get(call.getArgument(0))));
    }

    private Actor actor(String role) {
        User user = new User(); user.setEmail("bang" + UUID.randomUUID() + "@gmail.com");
        user.setFullName("Chat " + role); user.setEnabled(true); user.setPasswordHash("test-only-not-used-for-login");
        user.getRoles().add(roles.findByCode(role).orElseThrow()); user = users.saveAndFlush(user);
        AuthSession session = new AuthSession(); session.setSessionId(UUID.randomUUID().toString()); session.setUser(user);
        session.setRefreshTokenHash("0".repeat(64)); session.setExpiresAt(Instant.now().plusSeconds(3600));
        session = sessions.saveAndFlush(session);
        return new Actor(user, session, jwt.accessToken(user.getEmail(), session.getSessionId(), List.of()));
    }
    @Test
    void messageSearchIsPrivateLiteralAndPaginatesBeyondLoadedHistory() throws Exception {
        Actor a = actor("STUDENT"), b = actor("EXPERT"), outsider = actor("ADMIN");
        String keyword = "search-" + UUID.randomUUID();
        for (int n = 0; n < 52; n++) chat.send(a.user().getId(), b.user().getId(), keyword + " " + n, UUID.randomUUID().toString());
        chat.send(outsider.user().getId(), b.user().getId(), keyword + " secret", UUID.randomUUID().toString());
        chat.send(a.user().getId(), null, keyword + " public", UUID.randomUUID().toString());
        String endpoint = "/api/v1/chat/messages/search";
        var first = json.readTree(mvc.perform(get(endpoint).param("peerId", b.user().getId().toString())
            .param("query", keyword.toUpperCase()).header("Authorization", "Bearer " + a.token()))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertEquals(50, first.size()); assertFalse(first.toString().contains("secret")); assertFalse(first.toString().contains("public"));
        mvc.perform(get(endpoint).param("peerId", b.user().getId().toString()).param("query", keyword)
            .param("before", first.get(49).path("id").asText()).header("Authorization", "Bearer " + a.token()))
            .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(2));
        mvc.perform(get(endpoint).param("query", keyword).header("Authorization", "Bearer " + a.token()))
            .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1));
        chat.send(a.user().getId(), b.user().getId(), keyword + " 100%_!", UUID.randomUUID().toString());
        mvc.perform(get(endpoint).param("peerId", b.user().getId().toString()).param("query", "%_!")
            .header("Authorization", "Bearer " + a.token())).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1));
        mvc.perform(get(endpoint).param("query", " ").header("Authorization", "Bearer " + a.token())).andExpect(status().isBadRequest());
        mvc.perform(get(endpoint).param("query", "x")).andExpect(status().isUnauthorized());
    }

    @Test
    void directoryMatchesOnlyNamesAndTreatsWildcardsLiterally() throws Exception {
        Actor a = actor("STUDENT"), b = actor("EXPERT");
        String name = "Bang-" + UUID.randomUUID() + "%_!";
        b.user().setFullName(name); users.saveAndFlush(b.user());
        mvc.perform(get("/api/v1/chat/users").param("query", name.toUpperCase()).header("Authorization", "Bearer " + a.token()))
            .andExpect(status().isOk()).andExpect(jsonPath("$.content.length()").value(1))
            .andExpect(jsonPath("$.content[0].id").value(b.user().getId()));
        mvc.perform(get("/api/v1/chat/users").param("query", b.user().getEmail()).header("Authorization", "Bearer " + a.token()))
            .andExpect(status().isOk()).andExpect(jsonPath("$.content.length()").value(0));
        b.user().setBlocked(true); users.saveAndFlush(b.user());
        mvc.perform(get("/api/v1/chat/users").param("query", name).header("Authorization", "Bearer " + a.token()))
            .andExpect(status().isOk()).andExpect(jsonPath("$.content.length()").value(0));
    }

    private final class Socket implements WebSocket.Listener, AutoCloseable {
        final BlockingQueue<JsonNode> frames = new LinkedBlockingQueue<>();
        final CompletableFuture<Integer> closed = new CompletableFuture<>();
        final StringBuilder partial = new StringBuilder();
        WebSocket ws;
        Socket(String origin) throws Exception {
            ws = HttpClient.newHttpClient().newWebSocketBuilder().header("Origin", origin)
                .buildAsync(URI.create("ws://localhost:" + port + "/ws/chat"), this).get(8, TimeUnit.SECONDS);
        }
        public void onOpen(WebSocket socket) { socket.request(1); }
        public CompletionStage<?> onText(WebSocket socket, CharSequence text, boolean last) {
            partial.append(text);
            if (last) {
                try {
                    JsonNode frame = json.readTree(partial.toString());
                    if (!"PRESENCE".equals(frame.path("type").asText())) frames.add(frame);
                } catch (Exception ex) { closed.completeExceptionally(ex); }
                partial.setLength(0);
            }
            socket.request(1); return CompletableFuture.completedFuture(null);
        }
        public CompletionStage<?> onClose(WebSocket socket, int status, String reason) { closed.complete(status); return null; }
        public void onError(WebSocket socket, Throwable error) { closed.completeExceptionally(error); }
        void send(Map<String, ?> frame) throws Exception { ws.sendText(json.writeValueAsString(frame), true).get(8, TimeUnit.SECONDS); }
        JsonNode next() throws Exception { JsonNode f = frames.poll(8, TimeUnit.SECONDS); assertNotNull(f, "Socket frame timed out"); return f; }
        void message(Long recipient, String content, String id) throws Exception {
            Map<String, Object> frame = new HashMap<>(); frame.put("type", "SEND"); frame.put("recipientId", recipient);
            frame.put("content", content); frame.put("clientMessageId", id); send(frame);
        }
        public void close() { ws.abort(); }
    }
    private Socket socket(Actor actor) throws Exception {
        Socket socket = new Socket("http://localhost:5173"); socket.send(Map.of("type", "AUTH", "token", actor.token()));
        assertEquals("READY", socket.next().path("type").asText()); return socket;
    }
    private JsonNode history(Actor actor, Long peer) throws Exception {
        var request = get("/api/v1/chat/messages").header("Authorization", "Bearer " + actor.token());
        if (peer != null) request.param("peerId", peer.toString());
        return json.readTree(mvc.perform(request).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }

    @ParameterizedTest @ValueSource(strings = {"STUDENT", "EXPERT", "MANAGER", "ADMIN"})
    void allRolesCanDiscoverUsersAndExchangeCommunityMessages(String role) throws Exception {
        Actor a = actor(role), b = actor("EXPERT");
        mvc.perform(get("/api/v1/chat/users").param("query", "Chat").header("Authorization", "Bearer " + a.token()))
            .andExpect(status().isOk()).andExpect(jsonPath("$.content[0].passwordHash").doesNotExist())
            .andExpect(jsonPath("$.content[0].email").doesNotExist());
        try (Socket one = socket(a); Socket two = socket(b)) {
            String id = UUID.randomUUID().toString(); one.message(null, " Xin chào mọi người ", id);
            JsonNode sent = one.next().path("message"), received = two.next().path("message");
            assertEquals(sent.path("id"), received.path("id")); assertEquals("Xin chào mọi người", received.path("content").asText());
            assertEquals(a.user().getId().longValue(), received.path("sender").path("id").asLong());
            assertTrue(history(b, null).toString().contains(id));
        }
    }

    @Test void privateMessagesAreOnlyDeliveredAndQueryableByParticipantsAndRetryDoesNotDuplicate() throws Exception {
        Actor a = actor("STUDENT"), b = actor("ADMIN"), stranger = actor("MANAGER");
        try (Socket one = socket(a); Socket two = socket(b); Socket three = socket(stranger)) {
            String id = UUID.randomUUID().toString(); one.message(b.user().getId(), "Tin riêng", id);
            long savedId = one.next().path("message").path("id").asLong();
            assertEquals(savedId, two.next().path("message").path("id").asLong());
            assertNull(three.frames.poll(300, TimeUnit.MILLISECONDS));
            assertEquals(1, history(a, b.user().getId()).size()); assertEquals(1, history(b, a.user().getId()).size());
            assertEquals(0, history(stranger, b.user().getId()).size());
            assertFalse(history(stranger, null).toString().contains(id));
            one.message(b.user().getId(), "Tin riêng", id);
            assertEquals(savedId, one.next().path("message").path("id").asLong()); two.next();
            assertEquals(1, history(a, b.user().getId()).size());
            one.message(b.user().getId(), "Nội dung khác", id);
            assertEquals("ERROR", one.next().path("type").asText());
        }
    }

    @Test void invalidInputsDoNotPersistAndSocketRemainsUsable() throws Exception {
        Actor a = actor("STUDENT"), b = actor("EXPERT");
        b.user().setBlocked(true); users.saveAndFlush(b.user());
        try (Socket socket = socket(a)) {
            for (String text : List.of(" ", "x".repeat(2001))) {
                socket.message(null, text, UUID.randomUUID().toString()); assertEquals("ERROR", socket.next().path("type").asText());
            }
            for (Long recipient : List.of(a.user().getId(), b.user().getId(), -1L, Long.MAX_VALUE)) {
                socket.message(recipient, "Không gửi", UUID.randomUUID().toString()); assertEquals("ERROR", socket.next().path("type").asText());
            }
            socket.message(null, "ok", "not-a-uuid"); assertEquals("ERROR", socket.next().path("type").asText());
            socket.ws.sendText("not-json", true).get(); assertEquals("ERROR", socket.next().path("type").asText());
            socket.message(null, "ok", UUID.randomUUID().toString()); assertEquals("MESSAGE", socket.next().path("type").asText());
        }
    }

    @Test void guestRefreshTokenWrongOriginAndRevokedOrBlockedUsersCannotUseChat() throws Exception {
        Actor a = actor("STUDENT");
        mvc.perform(get("/api/v1/chat/users")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/chat/messages").header("Authorization", "Bearer " + jwt.refreshToken(a.user().getEmail(), a.session().getSessionId())))
            .andExpect(status().isUnauthorized());
        assertThrows(Exception.class, () -> new Socket("https://untrusted.invalid"));
        try (Socket raw = new Socket("http://localhost:5173")) {
            raw.send(Map.of("type", "AUTH", "token", jwt.refreshToken(a.user().getEmail(), a.session().getSessionId())));
            assertEquals(1008, raw.closed.get(8, TimeUnit.SECONDS));
        }
        try (Socket raw = new Socket("http://localhost:5173")) {
            raw.message(null, "anonymous", UUID.randomUUID().toString()); assertEquals(1008, raw.closed.get(8, TimeUnit.SECONDS));
        }
        try (Socket socket = socket(a)) {
            a.session().setRevokedAt(Instant.now()); sessions.saveAndFlush(a.session());
            socket.message(null, "revoked", UUID.randomUUID().toString()); assertEquals(1008, socket.closed.get(8, TimeUnit.SECONDS));
        }
        Actor blocked = actor("ADMIN"); blocked.user().setBlocked(true); users.saveAndFlush(blocked.user());
        mvc.perform(get("/api/v1/chat/users").header("Authorization", "Bearer " + blocked.token())).andExpect(status().isUnauthorized());
    }

    @Test void historyUsesStableCursorPaginationAndBoundsInputs() throws Exception {
        Actor a = actor("STUDENT"), b = actor("EXPERT");
        for (int i = 0; i < 55; i++) chat.send(a.user().getId(), b.user().getId(), "Message " + i, UUID.randomUUID().toString());
        JsonNode latest = history(a, b.user().getId()); assertEquals(50, latest.size());
        JsonNode older = json.readTree(mvc.perform(get("/api/v1/chat/messages").header("Authorization", "Bearer " + a.token())
            .param("peerId", b.user().getId().toString()).param("before", latest.get(0).path("id").asText()))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertEquals(5, older.size()); assertTrue(older.get(4).path("id").asLong() < latest.get(0).path("id").asLong());
        mvc.perform(get("/api/v1/chat/messages").param("before", "0").header("Authorization", "Bearer " + a.token())).andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/chat/users").param("page", "-1").header("Authorization", "Bearer " + a.token())).andExpect(status().isBadRequest());
    }

    @Test void expiredOrMismatchedTokensAreRejectedAndRevokedRecipientDoesNotReceiveMessages() throws Exception {
        Actor a = actor("STUDENT"), b = actor("EXPERT");
        String expired = new JwtTokenProvider("test-secret-with-enough-entropy-for-hmac-signing-only", -1, 7)
            .accessToken(a.user().getEmail(), a.session().getSessionId(), List.of());
        String mismatched = jwt.accessToken(a.user().getEmail(), b.session().getSessionId(), List.of());
        for (String token : List.of(expired, mismatched)) {
            try (Socket raw = new Socket("http://localhost:5173")) {
                raw.send(Map.of("type", "AUTH", "token", token)); assertEquals(1008, raw.closed.get(8, TimeUnit.SECONDS));
            }
        }
        try (Socket sender = socket(a); Socket recipient = socket(b)) {
            b.session().setRevokedAt(Instant.now()); sessions.saveAndFlush(b.session());
            sender.message(b.user().getId(), "Saved for next login", UUID.randomUUID().toString());
            assertEquals("MESSAGE", sender.next().path("type").asText());
            assertEquals(1008, recipient.closed.get(8, TimeUnit.SECONDS)); assertTrue(recipient.frames.isEmpty());
            assertEquals(1, history(a, b.user().getId()).size());
        }
    }

    private byte[] image() {
        return Base64.getDecoder().decode("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+a5S8AAAAASUVORK5CYII=");
    }
    private long upload(Actor actor, Long recipient, String name, String type, byte[] bytes) throws Exception {
        var request = org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart("/api/v1/chat/attachments")
            .file(new org.springframework.mock.web.MockMultipartFile("file", name, type, bytes)).header("Authorization", "Bearer " + actor.token());
        if (recipient != null) request.param("recipientId", recipient.toString());
        return json.readTree(mvc.perform(request).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString()).path("id").asLong();
    }

    @Test void privateImageUploadSocketDeliveryAndDownloadRequireTheCorrectParticipants() throws Exception {
        Actor a = actor("STUDENT"), b = actor("EXPERT"), stranger = actor("ADMIN");
        long fileId = upload(a, b.user().getId(), "photo.png", "image/png", image());
        mvc.perform(get("/api/v1/chat/attachments/" + fileId).header("Authorization", "Bearer " + b.token())).andExpect(status().isForbidden());
        try (Socket sender = socket(a); Socket receiver = socket(b)) {
            String id = UUID.randomUUID().toString();
            Map<String, Object> frame = Map.of("type", "SEND", "recipientId", b.user().getId(), "content", "", "attachmentId", fileId, "clientMessageId", id);
            sender.send(frame); JsonNode saved = sender.next().path("message");
            assertEquals(fileId, saved.path("attachment").path("id").asLong()); assertEquals("image/png", receiver.next().path("message").path("attachment").path("contentType").asText());
            sender.send(frame); assertEquals(saved.path("id"), sender.next().path("message").path("id")); receiver.next();
            assertEquals(fileId, history(b, a.user().getId()).get(0).path("attachment").path("id").asLong());
        }
        for (Actor allowed : List.of(a, b)) mvc.perform(get("/api/v1/chat/attachments/" + fileId).header("Authorization", "Bearer " + allowed.token()))
            .andExpect(status().isOk()).andExpect(header().string("Content-Type", "image/png"))
            .andExpect(header().string("X-Content-Type-Options", "nosniff")).andExpect(content().bytes(image()));
        mvc.perform(get("/api/v1/chat/attachments/" + fileId).header("Authorization", "Bearer " + stranger.token())).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/chat/attachments/" + fileId)).andExpect(status().isUnauthorized());
        b.user().setBlocked(true); users.saveAndFlush(b.user());
        mvc.perform(get("/api/v1/chat/attachments/" + fileId).header("Authorization", "Bearer " + b.token())).andExpect(status().isUnauthorized());
    }

    @Test void videoCanBeSharedInCommunityButCannotBeStolenOrMovedBetweenConversations() throws Exception {
        Actor a = actor("MANAGER"), b = actor("STUDENT"), other = actor("ADMIN");
        byte[] video = new byte[]{0,0,0,24,'f','t','y','p','i','s','o','m',0,0,0,0,'i','s','o','m'};
        long fileId = upload(a, null, "clip.mp4", "video/mp4", video);
        assertThrows(com.onlinelearning.exception.ApiException.class, () -> chat.send(b.user().getId(), null, "", UUID.randomUUID().toString(), fileId));
        assertThrows(com.onlinelearning.exception.ApiException.class, () -> chat.send(a.user().getId(), b.user().getId(), "", UUID.randomUUID().toString(), fileId));
        try (Socket sender = socket(a); Socket receiver = socket(b)) {
            sender.send(Map.of("type", "SEND", "content", "Video", "attachmentId", fileId, "clientMessageId", UUID.randomUUID().toString()));
            sender.next(); assertEquals("video/mp4", receiver.next().path("message").path("attachment").path("contentType").asText());
        }
        mvc.perform(get("/api/v1/chat/attachments/" + fileId).header("Authorization", "Bearer " + other.token())).andExpect(status().isOk()).andExpect(content().bytes(video));
        assertThrows(com.onlinelearning.exception.ApiException.class, () -> chat.send(a.user().getId(), null, "Again", UUID.randomUUID().toString(), fileId));
    }

    @Test void invalidAndOversizedUploadsAreRejectedEvenWithForgedContentType() throws Exception {
        Actor a = actor("STUDENT");
        for (byte[] bytes : List.of(new byte[0], "<svg onload='alert(1)'/>".getBytes(), new byte[5 * 1024 * 1024 + 1])) {
            if (bytes.length > 100) System.arraycopy(image(), 0, bytes, 0, image().length);
            var req = org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart("/api/v1/chat/attachments")
                .file(new org.springframework.mock.web.MockMultipartFile("file", "fake.png", "image/png", bytes)).header("Authorization", "Bearer " + a.token());
            mvc.perform(req).andExpect(bytes.length > 100 ? status().isPayloadTooLarge() : status().isBadRequest());
        }
    }

    @ParameterizedTest @ValueSource(strings = {"STUDENT", "EXPERT", "MANAGER", "ADMIN"})
    void everyRoleCanUploadAndServerUsesActualMediaType(String role) throws Exception {
        Actor owner = actor(role);
        long id = upload(owner, null, "fake-extension.mp4", "text/html", image());
        mvc.perform(get("/api/v1/chat/attachments/" + id).header("Authorization", "Bearer " + owner.token()))
            .andExpect(status().isOk()).andExpect(header().string("Content-Type", "image/png"));
        assertNotNull(attachments.findById(id).orElseThrow().getCloudPublicId());
        assertEquals("authenticated", attachments.findById(id).orElseThrow().getCloudDeliveryType());
    }

    @Test void cloudinaryFailureDoesNotSaveAnAttachmentOrLeaveTemporaryFile() throws Exception {
        Actor owner = actor("STUDENT"); long before = attachments.count();
        java.util.concurrent.atomic.AtomicReference<Path> temporary = new java.util.concurrent.atomic.AtomicReference<>();
        when(storage.upload(any(Path.class), anyString(), anyBoolean())).thenAnswer(call -> {
            temporary.set(call.getArgument(0));
            throw new com.onlinelearning.exception.ApiException(org.springframework.http.HttpStatus.BAD_GATEWAY, "Cloudinary unavailable");
        });
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart("/api/v1/chat/attachments")
            .file(new org.springframework.mock.web.MockMultipartFile("file", "image.png", "image/png", image()))
            .header("Authorization", "Bearer " + owner.token())).andExpect(status().isBadGateway());
        assertEquals(before, attachments.count()); assertFalse(Files.exists(temporary.get()));
    }
}

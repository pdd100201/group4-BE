package com.onlinelearning.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.onlinelearning.api.ChatMediaStorage;
import com.onlinelearning.api.MediaStorage.StoredMedia;
import com.onlinelearning.config.JwtTokenProvider;
import com.onlinelearning.entity.*;
import com.onlinelearning.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Access rules are exercised through HTTP, JPA and real WebSocket frames using isolated H2. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {"spring.datasource.url=jdbc:h2:mem:chat_test;MODE=MySQL;DB_CLOSE_DELAY=-1", "ngrok.enabled=false"})
@AutoConfigureMockMvc @ActiveProfiles("test")
class ChatIntegrationTests {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired UserRepository users;
    @Autowired RoleRepository roles;
    @Autowired CourseRepository courses;
    @Autowired CourseRegistrationRepository registrations;
    @Autowired AuthSessionRepository sessions;
    @Autowired ChatService chat;
    @Autowired JwtTokenProvider jwt;
    @MockitoBean ChatMediaStorage storage;
    @LocalServerPort int port;
    record Actor(User user, AuthSession session, String token) { }
    private final Map<String, byte[]> cloudFiles = new ConcurrentHashMap<>();

    @BeforeEach void fakePrivateStorage() {
        cloudFiles.clear();
        when(storage.upload(any(Path.class), anyString(), anyBoolean())).thenAnswer(call -> {
            String id = "chat-test/" + UUID.randomUUID(); byte[] bytes = Files.readAllBytes(call.getArgument(0));
            cloudFiles.put(id, bytes);
            return new StoredMedia(id, "image", "authenticated", "png", "https://cloudinary.invalid/private", bytes.length);
        });
        when(storage.download(anyString(), anyString(), anyString()))
            .thenAnswer(call -> new ByteArrayResource(cloudFiles.get(call.getArgument(0))));
    }

    private Actor actor(String role) {
        User user = new User(); user.setEmail("chat" + UUID.randomUUID() + "@example.com");
        user.setFullName("Chat " + role); user.setEnabled(true); user.setPasswordHash("unused-test-hash");
        user.getRoles().add(roles.findByCode(role).orElseThrow()); user = users.saveAndFlush(user);
        AuthSession session = new AuthSession(); session.setSessionId(UUID.randomUUID().toString());
        session.setUser(user); session.setRefreshTokenHash("0".repeat(64));
        session.setExpiresAt(Instant.now().plusSeconds(3600)); session = sessions.saveAndFlush(session);
        return new Actor(user, session, jwt.accessToken(user.getEmail(), session.getSessionId(), List.of()));
    }

    private Course course(Actor expert) {
        Course course = new Course(); course.setTitle("Khóa " + UUID.randomUUID()); course.setExpert(expert.user());
        return courses.saveAndFlush(course);
    }

    private CourseRegistration enroll(Actor student, Course course) {
        CourseRegistration registration = new CourseRegistration(); registration.setStudent(student.user());
        registration.setCourse(course); registration.setStatus(RegistrationStatus.ACTIVE);
        registration.setValidFrom(Instant.now().minusSeconds(60));
        registration.setValidUntil(Instant.now().plusSeconds(3600)); registration.setLifetimeSnapshot(false);
        return registrations.saveAndFlush(registration);
    }

    private JsonNode history(Actor actor, Actor peer, Course course) throws Exception {
        return json.readTree(mvc.perform(get("/api/v1/chat/messages")
            .param("peerId", peer.user().getId().toString()).param("courseId", course.getId().toString())
            .header("Authorization", "Bearer " + actor.token())).andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString());
    }

    @Test void directoryShowsOnlyCurrentCourseRelationshipsAndNeverGlobalUsers() throws Exception {
        Actor student = actor("STUDENT"), expert = actor("EXPERT"), otherExpert = actor("EXPERT"), admin = actor("ADMIN");
        Course first = course(expert), second = course(otherExpert);
        CourseRegistration registration = enroll(student, first);
        mvc.perform(get("/api/v1/chat/users").header("Authorization", "Bearer " + student.token()))
            .andExpect(status().isOk()).andExpect(jsonPath("$.content.length()").value(1))
            .andExpect(jsonPath("$.content[0].courseId").value(first.getId()))
            .andExpect(jsonPath("$.content[0].peer.id").value(expert.user().getId()))
            .andExpect(jsonPath("$.content[0].peer.email").doesNotExist());
        mvc.perform(get("/api/v1/chat/users").param("query", second.getTitle())
            .header("Authorization", "Bearer " + student.token()))
            .andExpect(status().isOk()).andExpect(jsonPath("$.content.length()").value(0));
        mvc.perform(get("/api/v1/chat/users").header("Authorization", "Bearer " + expert.token()))
            .andExpect(status().isOk()).andExpect(jsonPath("$.content[0].peer.id").value(student.user().getId()));
        mvc.perform(get("/api/v1/chat/users").header("Authorization", "Bearer " + admin.token()))
            .andExpect(status().isOk()).andExpect(jsonPath("$.content.length()").value(0));
        registration.setValidUntil(Instant.now().minusSeconds(1)); registrations.saveAndFlush(registration);
        mvc.perform(get("/api/v1/chat/users").header("Authorization", "Bearer " + student.token()))
            .andExpect(status().isOk()).andExpect(jsonPath("$.content.length()").value(0));
    }

    @Test void historySearchAndSendAreIsolatedByCourseAndEnrollment() throws Exception {
        Actor student = actor("STUDENT"), expert = actor("EXPERT"), outsider = actor("STUDENT"), admin = actor("ADMIN");
        Course first = course(expert), second = course(expert);
        CourseRegistration registration = enroll(student, first); enroll(student, second);
        String id = UUID.randomUUID().toString();
        chat.send(student.user().getId(), expert.user().getId(), first.getId(), "first 100%_!", id);
        chat.send(student.user().getId(), expert.user().getId(), second.getId(), "second", UUID.randomUUID().toString());
        assertEquals(1, history(student, expert, first).size());
        assertEquals(1, history(expert, student, second).size());
        assertFalse(history(student, expert, first).toString().contains("second"));
        mvc.perform(get("/api/v1/chat/messages/search").param("peerId", expert.user().getId().toString())
            .param("courseId", first.getId().toString()).param("query", "%_!")
            .header("Authorization", "Bearer " + student.token()))
            .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1));
        mvc.perform(get("/api/v1/chat/messages").param("peerId", expert.user().getId().toString())
            .param("courseId", first.getId().toString()).header("Authorization", "Bearer " + outsider.token()))
            .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/chat/messages/search").param("peerId", student.user().getId().toString())
            .param("courseId", first.getId().toString()).param("query", "first")
            .header("Authorization", "Bearer " + admin.token())).andExpect(status().isForbidden());
        assertThrows(com.onlinelearning.exception.ApiException.class,
            () -> chat.send(student.user().getId(), null, null, "global", UUID.randomUUID().toString()));
        assertThrows(com.onlinelearning.exception.ApiException.class,
            () -> chat.send(student.user().getId(), admin.user().getId(), first.getId(), "admin", UUID.randomUUID().toString()));
        registration.setStatus(RegistrationStatus.EXPIRED); registrations.saveAndFlush(registration);
        mvc.perform(get("/api/v1/chat/messages").param("peerId", expert.user().getId().toString())
            .param("courseId", first.getId().toString()).header("Authorization", "Bearer " + student.token()))
            .andExpect(status().isForbidden());
    }

    private final class Socket implements WebSocket.Listener, AutoCloseable {
        final BlockingQueue<JsonNode> frames = new LinkedBlockingQueue<>();
        final StringBuilder partial = new StringBuilder();
        WebSocket ws;
        Socket(Actor actor) throws Exception {
            ws = HttpClient.newHttpClient().newWebSocketBuilder().header("Origin", "http://localhost:5173")
                .buildAsync(URI.create("ws://localhost:" + port + "/ws/chat"), this).get(8, TimeUnit.SECONDS);
            send(Map.of("type", "AUTH", "token", actor.token()));
            assertEquals("READY", next().path("type").asText());
        }
        public void onOpen(WebSocket socket) { socket.request(1); }
        public CompletionStage<?> onText(WebSocket socket, CharSequence text, boolean last) {
            partial.append(text);
            if (last) {
                try {
                    JsonNode frame = json.readTree(partial.toString());
                    if (!"PRESENCE".equals(frame.path("type").asText())) frames.add(frame);
                } catch (Exception ignored) { }
                partial.setLength(0);
            }
            socket.request(1); return CompletableFuture.completedFuture(null);
        }
        void send(Map<String, ?> frame) throws Exception { ws.sendText(json.writeValueAsString(frame), true).get(8, TimeUnit.SECONDS); }
        JsonNode next() throws Exception { JsonNode frame = frames.poll(8, TimeUnit.SECONDS); assertNotNull(frame); return frame; }
        public void close() { ws.abort(); }
    }

    @Test void socketRejectsWrongCourseAndDeliversOnlyToParticipants() throws Exception {
        Actor student = actor("STUDENT"), expert = actor("EXPERT"), outsider = actor("ADMIN");
        Course course = course(expert); Course other = course(expert); enroll(student, course);
        try (Socket sender = new Socket(student); Socket receiver = new Socket(expert); Socket third = new Socket(outsider)) {
            String id = UUID.randomUUID().toString();
            sender.send(Map.of("type", "SEND", "recipientId", expert.user().getId(), "courseId", course.getId(),
                "content", "Hỏi bài", "clientMessageId", id));
            assertEquals("MESSAGE", sender.next().path("type").asText());
            assertEquals(course.getId().longValue(), receiver.next().path("message").path("courseId").asLong());
            assertNull(third.frames.poll(300, TimeUnit.MILLISECONDS));
            sender.send(Map.of("type", "SEND", "recipientId", expert.user().getId(), "courseId", other.getId(),
                "content", "Sai khóa", "clientMessageId", UUID.randomUUID().toString()));
            assertEquals("ERROR", sender.next().path("type").asText());
            sender.send(Map.of("type", "SEND", "content", "Phòng chung", "clientMessageId", UUID.randomUUID().toString()));
            assertEquals("ERROR", sender.next().path("type").asText());
        }
    }

    @Test void attachmentCannotCrossCourseOrReachNonParticipants() throws Exception {
        Actor student = actor("STUDENT"), expert = actor("EXPERT"), admin = actor("ADMIN");
        Course first = course(expert), second = course(expert); enroll(student, first); enroll(student, second);
        byte[] image = Base64.getDecoder().decode("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+a5S8AAAAASUVORK5CYII=");
        var request = org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart("/api/v1/chat/attachments")
            .file(new org.springframework.mock.web.MockMultipartFile("file", "photo.png", "image/png", image))
            .param("recipientId", expert.user().getId().toString()).param("courseId", first.getId().toString())
            .header("Authorization", "Bearer " + student.token());
        long fileId = json.readTree(mvc.perform(request).andExpect(status().isCreated())
            .andReturn().getResponse().getContentAsString()).path("id").asLong();
        mvc.perform(get("/api/v1/chat/attachments/" + fileId).header("Authorization", "Bearer " + expert.token()))
            .andExpect(status().isForbidden());
        assertThrows(com.onlinelearning.exception.ApiException.class,
            () -> chat.send(student.user().getId(), expert.user().getId(), second.getId(), "", UUID.randomUUID().toString(), fileId));
        chat.send(student.user().getId(), expert.user().getId(), first.getId(), "", UUID.randomUUID().toString(), fileId);
        mvc.perform(get("/api/v1/chat/attachments/" + fileId).header("Authorization", "Bearer " + expert.token()))
            .andExpect(status().isOk()).andExpect(content().bytes(image));
        mvc.perform(get("/api/v1/chat/attachments/" + fileId).header("Authorization", "Bearer " + admin.token()))
            .andExpect(status().isForbidden());
    }
}

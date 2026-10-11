package com.onlinelearning.service;
import com.onlinelearning.api.MediaStorage;
import com.onlinelearning.dto.course.*;
import com.onlinelearning.entity.*;
import com.onlinelearning.exception.ApiException;
import com.onlinelearning.repository.*;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.Instant;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest @ActiveProfiles("test") @Transactional @AutoConfigureMockMvc
class MediaIntegrationTests {
    @Autowired MediaService media;
    @Autowired ExpertCourseService courses;
    @Autowired ExpertLessonService lessons;
    @Autowired MediaAssetRepository assets;
    @Autowired CourseRepository courseRepository;
    @Autowired UserRepository users;
    @Autowired EntityManager em;
    @Autowired MockMvc mvc;
    @MockitoBean MediaStorage storage;
    static final String OWNER = "expert@onlinelearning.local";
    MockMultipartFile image() { return new MockMultipartFile("file", "test.png", "image/png", new byte[]{(byte)137,80,78,71,13,10,26,10,0}); }
    MockMultipartFile video() { return new MockMultipartFile("file", "test.mp4", "video/mp4", new byte[]{0,0,0,16,102,116,121,112,105,115,111,109,0,0,0,0}); }
    Long course() { return courses.create(OWNER, new CourseRequest("Course", "Description", "Java", BigDecimal.ZERO)).id(); }
    void mock(boolean video) {
        when(storage.upload(any(Path.class), anyString(), eq(video))).thenReturn(new MediaStorage.StoredMedia("ols/test", video ? "video" : "image", video ? "authenticated" : "upload", video ? "mp4" : "png", "https://res.cloudinary.com/test/asset", 16));
        when(storage.playback(anyString(), anyString())).thenReturn("https://api.cloudinary.com/test/signed-video");
    }
    @Test void avatarUploadReturnsPublicUrlForEveryRole() throws Exception {
        mock(false);
        for (String actor : new String[]{"admin", "manager", "expert", "student"})
            mvc.perform(multipart("/api/v1/media").file(image()).param("purpose", "AVATAR").with(user(actor + "@onlinelearning.local").roles(actor.toUpperCase())))
                .andExpect(status().isCreated()).andExpect(jsonPath("url").value("https://res.cloudinary.com/test/asset"));
        verify(storage, times(4)).upload(any(Path.class), anyString(), eq(false));
    }
    @Test void courseThumbnailAndLessonVideoPersistById() {
        mock(false); mock(true); Long course = course();
        var image = media.upload(OWNER, image(), "COURSE_IMAGE", course);
        var updated = courses.update(course, OWNER, new CourseRequest("Course", "Description", "Java", BigDecimal.ZERO, image.id()));
        assertEquals(image.url(), updated.thumbnailUrl());
        var video = media.upload(OWNER, video(), "LESSON_VIDEO", course); assertNull(video.url());
        var lesson = lessons.create(course, OWNER, new LessonRequest("Lesson", "Content", 1, true, video.id()));
        assertEquals(video.id(), lesson.videoMediaId());
        assertNotNull(media.playback(video.id(), OWNER));
        assertNotNull(media.playback(video.id(), "manager@onlinelearning.local"));
    }
    @Test void publishedThumbnailCanChangeWithoutOpeningLessonVideoUploads() {
        mock(false); Long id = course();
        courseRepository.findById(id).orElseThrow().setStatus(CourseStatus.PUBLISHED);
        var uploaded = media.upload(OWNER, image(), "COURSE_IMAGE", id);
        var updated = courses.update(id, OWNER, new CourseRequest("Published", "Description", "Java", BigDecimal.ZERO, uploaded.id()));
        assertEquals(CourseStatus.PUBLISHED, updated.status());
        assertEquals(uploaded.url(), updated.thumbnailUrl());
        assertEquals(409, assertThrows(ApiException.class, () -> media.upload(OWNER, video(), "LESSON_VIDEO", id)).status().value());
        assertEquals(403, assertThrows(ApiException.class, () -> media.upload("manager@onlinelearning.local", image(), "COURSE_IMAGE", id)).status().value());
    }
    @Test void wrongOwnerWrongPurposeAndLockedCourseCannotUploadOrBind() {
        mock(false); Long course = course();
        assertEquals(403, assertThrows(ApiException.class, () -> media.upload("student@onlinelearning.local", image(), "COURSE_IMAGE", course)).status().value());
        var avatar = media.upload(OWNER, image(), "AVATAR", null);
        assertThrows(ApiException.class, () -> media.attachment(avatar.id(), OWNER, course, "COURSE_IMAGE"));
        var image = media.upload(OWNER, image(), "COURSE_IMAGE", course);
        assertThrows(ApiException.class, () -> media.attachment(image.id(), OWNER, course(), "COURSE_IMAGE"));
        courseRepository.findById(course).orElseThrow().setStatus(CourseStatus.PENDING_REVIEW);
        assertEquals(409, assertThrows(ApiException.class, () -> media.upload(OWNER, image(), "COURSE_IMAGE", course)).status().value());
    }
    @Test void corruptEmptyOversizeAndProviderFailureAreReported() {
        assertEquals(400, assertThrows(ApiException.class, () -> media.upload(OWNER, new MockMultipartFile("file", "x.png", "image/png", "not an image".getBytes()), "AVATAR", null)).status().value());
        assertThrows(ApiException.class, () -> media.upload(OWNER, new MockMultipartFile("file", new byte[0]), "AVATAR", null));
        MockMultipartFile huge = new MockMultipartFile("file", "x.png", "image/png", new byte[]{1}) { public long getSize() { return 11L * 1024 * 1024; } };
        assertEquals(413, assertThrows(ApiException.class, () -> media.upload(OWNER, huge, "AVATAR", null)).status().value());
        verifyNoInteractions(storage);
        when(storage.upload(any(Path.class), anyString(), eq(false))).thenThrow(new ApiException(org.springframework.http.HttpStatus.BAD_GATEWAY, "Provider unavailable"));
        long before = assets.count();
        assertEquals(502, assertThrows(ApiException.class, () -> media.upload(OWNER, image(), "AVATAR", null)).status().value());
        assertEquals(before, assets.count());
    }
    @Test void studentPlaybackRequiresActiveRegistrationAndVisibleLinkedLesson() {
        mock(true); Long course = course(); var video = media.upload(OWNER, video(), "LESSON_VIDEO", course);
        lessons.create(course, OWNER, new LessonRequest("Lesson", "Content", 1, true, video.id()));
        courseRepository.findById(course).orElseThrow().setStatus(CourseStatus.PUBLISHED);
        assertEquals(403, assertThrows(ApiException.class, () -> media.playback(video.id(), "student@onlinelearning.local")).status().value());
        CourseRegistration r = new CourseRegistration(); r.setCourse(courseRepository.findById(course).orElseThrow()); r.setStudent(users.findByEmailIgnoreCase("student@onlinelearning.local").orElseThrow());
        r.setStatus(RegistrationStatus.ACTIVE); r.setValidFrom(Instant.now().minusSeconds(60)); r.setValidUntil(Instant.now().plusSeconds(3600)); em.persist(r); em.flush();
        assertNotNull(media.playback(video.id(), "student@onlinelearning.local"));
        r.setStatus(RegistrationStatus.CANCELLED);
        assertThrows(ApiException.class, () -> media.playback(video.id(), "student@onlinelearning.local"));
    }
    @Test void anonymousMediaUploadAndPlaybackAreDenied() throws Exception {
        mvc.perform(multipart("/api/v1/media").file(image()).param("purpose", "AVATAR")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/media/1/playback")).andExpect(status().isUnauthorized());
    }
    @Test void deletingDraftDetachesUnusedCloudRecordsWithoutDeletingAssets() {
        mock(false); Long course = course(); var image = media.upload(OWNER, image(), "COURSE_IMAGE", course);
        courses.update(course, OWNER, new CourseRequest("Course", "Description", "Java", BigDecimal.ZERO, image.id()));
        courses.delete(course, OWNER); em.flush();
        assertFalse(courseRepository.existsById(course)); assertNull(assets.findById(image.id()).orElseThrow().getCourse());
    }
}

package com.onlinelearning.service;

import com.onlinelearning.dto.course.*;
import com.onlinelearning.entity.*;
import com.onlinelearning.exception.ApiException;
import com.onlinelearning.repository.*;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ExpertLessonServiceTest {
    @Mock CourseRepository courses;
    @Mock LessonRepository lessons;
    @Mock AuditLogRepository audit;
    @Mock EntityManager em;
    @Mock MediaService media;
    @Mock TypedQuery<Long> retained;
    @InjectMocks ExpertLessonService service;
    Course course;
    Lesson lesson;
    static final String EMAIL = "expert@test.local";
    final LessonRequest input = new LessonRequest("  Java Core  ", "  Lesson content  ", 2, true, 10L);
    @BeforeEach void fixture() {
        User expert = new User(); expert.setId(3L); expert.setEmail(EMAIL);
        course = new Course(); course.setId(1L); course.setExpert(expert);
        lesson = new Lesson(); lesson.setId(7L); lesson.setCourse(course); lesson.setTitle("Existing"); lesson.setContent("Content"); lesson.setPosition(1); lesson.setActive(true);
    }
    void writable() { when(courses.findForUpdate(1L)).thenReturn(Optional.of(course)); }
    void readable() { when(courses.findById(1L)).thenReturn(Optional.of(course)); }
    void existing() { when(lessons.findByIdAndCourseId(7L, 1L)).thenReturn(Optional.of(lesson)); }
    void error(HttpStatus status, org.junit.jupiter.api.function.Executable action) {
        assertEquals(status, assertThrows(ApiException.class, action).status());
    }
    void checkAudit(String action) {
        ArgumentCaptor<AuditLog> capture = ArgumentCaptor.forClass(AuditLog.class);
        verify(audit).save(capture.capture()); var log = capture.getValue();
        assertEquals(action, log.getAction()); assertEquals("LESSON", log.getTargetType());
        assertEquals("7", log.getTargetId()); assertSame(course.getExpert(), log.getActor());
        assertEquals("courseId=1", log.getMetadata()); assertNotNull(log.getOccurredAt());
    }
    @Test void listReturnsRepositoryOrderIncludingHiddenLessons() {
        readable(); lesson.setActive(false); when(lessons.findByCourseIdOrderByPositionAscIdAsc(1L)).thenReturn(List.of(lesson));
        var result = service.list(1L, EMAIL.toUpperCase());
        assertEquals(1, result.size()); assertEquals(7L, result.getFirst().id()); assertFalse(result.getFirst().active());
        verifyNoInteractions(audit, media);
    }
    @Test void emptyList() { readable(); when(lessons.findByCourseIdOrderByPositionAscIdAsc(1L)).thenReturn(List.of()); assertTrue(service.list(1L, EMAIL).isEmpty()); }
    @Test void getMapsVideo() {
        readable(); existing(); MediaAsset video = new MediaAsset(); video.setId(10L); lesson.setVideo(video);
        var result = service.get(1L, 7L, EMAIL); assertEquals(10L, result.videoMediaId()); assertEquals("Existing", result.title());
    }
    @Test void readMissingCourse() { error(HttpStatus.NOT_FOUND, () -> service.list(1L, EMAIL)); verifyNoInteractions(lessons); }
    @Test void readOtherOwnersCourse() { readable(); error(HttpStatus.FORBIDDEN, () -> service.list(1L, "other@test.local")); verifyNoInteractions(lessons); }
    @Test void getMissingOrForeignLesson() { readable(); error(HttpStatus.NOT_FOUND, () -> service.get(1L, 7L, EMAIL)); verify(lessons).findByIdAndCourseId(7L, 1L); }
    @ParameterizedTest @EnumSource(value=CourseStatus.class, names={"DRAFT","REJECTED"})
    void createInEditableCourseTrimsContentAndAttachesVideo(CourseStatus status) {
        writable(); course.setStatus(status); MediaAsset video = new MediaAsset(); video.setId(10L);
        when(media.attachment(10L, EMAIL, 1L, "LESSON_VIDEO")).thenReturn(video);
        when(lessons.save(any(Lesson.class))).thenAnswer(call -> { Lesson saved = call.getArgument(0); saved.setId(7L); return saved; });
        var result = service.create(1L, EMAIL, input);
        assertEquals("Java Core", result.title()); assertEquals("Lesson content", result.content()); assertEquals(2, result.position()); assertTrue(result.active()); assertEquals(10L, result.videoMediaId());
        ArgumentCaptor<Lesson> capture = ArgumentCaptor.forClass(Lesson.class); verify(lessons).save(capture.capture()); assertSame(course, capture.getValue().getCourse());
        verify(lessons).existsByCourseIdAndPositionAndIdNot(1L, 2, -1L); checkAudit("LESSON_CREATED");
    }
    @Test void createHiddenLessonWithoutVideo() {
        writable(); when(lessons.save(any(Lesson.class))).thenAnswer(call -> { Lesson saved=call.getArgument(0); saved.setId(7L); return saved; });
        var result=service.create(1L, EMAIL, new LessonRequest("Title", "Content", 1, false));
        assertFalse(result.active()); assertNull(result.videoMediaId()); verify(media).attachment(null, EMAIL, 1L, "LESSON_VIDEO");
    }
    @Test void createDuplicatePositionDoesNotSaveOrAttach() {
        writable(); when(lessons.existsByCourseIdAndPositionAndIdNot(1L, 2, -1L)).thenReturn(true);
        error(HttpStatus.CONFLICT, () -> service.create(1L, EMAIL, input)); verify(lessons, never()).save(any()); verifyNoInteractions(media, audit);
    }
    @ParameterizedTest @EnumSource(value=CourseStatus.class, names={"DRAFT","REJECTED"}, mode=EnumSource.Mode.EXCLUDE)
    void lockedCourseRejectsAllWrites(CourseStatus status) {
        writable(); course.setStatus(status);
        error(HttpStatus.CONFLICT, () -> service.create(1L, EMAIL, input));
        error(HttpStatus.CONFLICT, () -> service.update(1L, 7L, EMAIL, input));
        error(HttpStatus.CONFLICT, () -> service.delete(1L, 7L, EMAIL)); verifyNoInteractions(lessons, media, audit, em);
    }
    @Test void otherOwnerCannotWrite() {
        writable(); error(HttpStatus.FORBIDDEN, () -> service.create(1L, "other@test.local", input));
        error(HttpStatus.FORBIDDEN, () -> service.update(1L, 7L, "other@test.local", input));
        error(HttpStatus.FORBIDDEN, () -> service.delete(1L, 7L, "other@test.local")); verifyNoInteractions(lessons, media, audit, em);
    }
    @Test void missingCourseCannotWrite() {
        error(HttpStatus.NOT_FOUND, () -> service.create(1L, EMAIL, input));
        error(HttpStatus.NOT_FOUND, () -> service.update(1L, 7L, EMAIL, input));
        error(HttpStatus.NOT_FOUND, () -> service.delete(1L, 7L, EMAIL)); verifyNoInteractions(lessons, media, audit, em);
    }
    @Test void updateKeepsIdentityAndAllowsCurrentPosition() {
        writable(); existing(); var result=service.update(1L, 7L, EMAIL, new LessonRequest(" New title ", " New content ", 1, false));
        assertEquals(7L, result.id()); assertEquals("New title", result.title()); assertEquals("New content", result.content()); assertFalse(result.active()); assertNull(result.videoMediaId());
        verify(lessons).existsByCourseIdAndPositionAndIdNot(1L, 1, 7L); verify(lessons, never()).save(any()); checkAudit("LESSON_UPDATED");
    }
    @Test void updateDuplicatePositionLeavesLessonUntouched() {
        writable(); existing(); when(lessons.existsByCourseIdAndPositionAndIdNot(1L, 2, 7L)).thenReturn(true);
        error(HttpStatus.CONFLICT, () -> service.update(1L, 7L, EMAIL, input)); assertEquals("Existing", lesson.getTitle()); verifyNoInteractions(media, audit);
    }
    @Test void updateMissingLesson() { writable(); error(HttpStatus.NOT_FOUND, () -> service.update(1L, 7L, EMAIL, input)); verifyNoInteractions(media, audit); }
    @Test void invalidVideoDoesNotPersistOrAuditCreation() {
        writable(); when(media.attachment(10L, EMAIL, 1L, "LESSON_VIDEO")).thenThrow(new ApiException(HttpStatus.FORBIDDEN,"Invalid video"));
        error(HttpStatus.FORBIDDEN, () -> service.create(1L, EMAIL, input)); verify(lessons, never()).save(any()); verifyNoInteractions(audit);
    }
    void progressCount(long count) {
        when(em.createQuery(anyString(), eq(Long.class))).thenReturn(retained); when(retained.setParameter("id", 7L)).thenReturn(retained); when(retained.getSingleResult()).thenReturn(count);
    }
    @Test void deleteWithoutProgressAuditsAndFlushes() {
        writable(); existing(); progressCount(0); service.delete(1L, 7L, EMAIL);
        checkAudit("LESSON_DELETED"); verify(lessons).delete(lesson); verify(lessons).flush();
    }
    @Test void deleteWithProgressIsRejectedWithoutAuditOrDelete() {
        writable(); existing(); progressCount(1); error(HttpStatus.CONFLICT, () -> service.delete(1L, 7L, EMAIL));
        verify(lessons, never()).delete(any()); verify(lessons, never()).flush(); verifyNoInteractions(audit);
    }
    @Test void deleteMissingLesson() { writable(); error(HttpStatus.NOT_FOUND, () -> service.delete(1L, 7L, EMAIL)); verifyNoInteractions(audit, em); }
}


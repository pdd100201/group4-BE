package com.onlinelearning.service;

import com.onlinelearning.entity.*;
import com.onlinelearning.exception.ApiException;
import com.onlinelearning.repository.*;
import jakarta.persistence.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Tracking RMS rows 34-35: UC27/28, BR26/27 retention. */
@ExtendWith(MockitoExtension.class)
class CourseModerationServiceTest {
    @Mock CourseRepository courses;
    @Mock UserRepository users;
    @Mock AuditLogRepository logs;
    @Mock EntityManager em;
    @InjectMocks CourseModerationService service;

    private User actor(String role) {
        User actor = new User(); actor.setEmail("actor@example.com");
        Role assigned = new Role(); assigned.setCode(role); actor.getRoles().add(assigned);
        when(users.findByEmailIgnoreCase(actor.getEmail())).thenReturn(Optional.of(actor)); return actor;
    }

    private Course course(CourseStatus status) {
        Course course = new Course(); course.setId(1L); course.setTitle("Original title"); course.setStatus(status);
        when(courses.findForUpdate(1L)).thenReturn(Optional.of(course)); return course;
    }

    @ParameterizedTest
    @ValueSource(strings={"MANAGER","ADMIN"})
    void uc27ModeratorArchivesPublishedCourseWithoutEditingContent(String role) {
        User actor = actor(role); Course course = course(CourseStatus.PUBLISHED);
        assertSame(course, service.archive(1L, actor.getEmail(), " Violation "));
        assertEquals(CourseStatus.ARCHIVED, course.getStatus()); assertEquals("Original title", course.getTitle());
        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(logs).save(captor.capture());
        assertEquals("COURSE_ARCHIVED", captor.getValue().getAction());
        assertEquals("Violation", captor.getValue().getMetadata()); assertSame(actor, captor.getValue().getActor());
        verifyNoInteractions(em);
    }

    @ParameterizedTest
    @EnumSource(value=CourseStatus.class, names="PUBLISHED", mode=EnumSource.Mode.EXCLUDE)
    void uc27RejectsArchivingNonPublishedCourse(CourseStatus status) {
        actor("MANAGER"); course(status);
        assertEquals(HttpStatus.CONFLICT, assertThrows(ApiException.class,
                () -> service.archive(1L, "actor@example.com", "Violation")).status());
        verifyNoInteractions(logs, em);
    }

    @ParameterizedTest
    @ValueSource(strings={"EXPERT","STUDENT"})
    void uc27And28RejectWrongRole(String role) {
        actor(role);
        assertEquals(HttpStatus.FORBIDDEN, assertThrows(ApiException.class,
                () -> service.archive(1L, "actor@example.com", "Violation")).status());
        assertEquals(HttpStatus.FORBIDDEN, assertThrows(ApiException.class,
                () -> service.delete(1L, "actor@example.com", "Violation")).status());
        verifyNoInteractions(courses, em, logs);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings={" ","   "})
    void uc27RequiresModerationReason(String reason) {
        actor("MANAGER");
        assertEquals(HttpStatus.BAD_REQUEST, assertThrows(ApiException.class,
                () -> service.archive(1L, "actor@example.com", reason)).status());
        verifyNoInteractions(courses, em, logs);
    }

    @Test
    void uc28RejectsOversizedReason() {
        actor("ADMIN");
        assertEquals(HttpStatus.BAD_REQUEST, assertThrows(ApiException.class,
                () -> service.delete(1L, "actor@example.com", "a".repeat(256))).status());
        verifyNoInteractions(courses, em, logs);
    }

    @ParameterizedTest
    @EnumSource(value=CourseStatus.class, names={"PENDING_REVIEW","APPROVED"})
    void uc28RejectsDeletingPendingOrLegacyCourse(CourseStatus status) {
        actor("ADMIN"); course(status);
        assertEquals(HttpStatus.CONFLICT, assertThrows(ApiException.class,
                () -> service.delete(1L, "actor@example.com", "Violation")).status());
        verifyNoInteractions(em, logs);
    }

    @ParameterizedTest
    @ValueSource(ints={0,1,2,3,4,5})
    void uc28RetainedReferencesPreventEveryHardDelete(int retainedIndex) {
        actor("MANAGER"); Course course = course(CourseStatus.PUBLISHED);
        TypedQuery<Long> count = mock(TypedQuery.class);
        when(em.createQuery(anyString(), eq(Long.class))).thenReturn(count);
        when(count.setParameter("id", 1L)).thenReturn(count);
        int[] index = {0};
        when(count.getSingleResult()).thenAnswer(invocation -> index[0]++ == retainedIndex ? 1L : 0L);

        assertEquals(HttpStatus.CONFLICT, assertThrows(ApiException.class,
                () -> service.delete(1L, "actor@example.com", "Violation")).status());

        assertEquals(CourseStatus.PUBLISHED, course.getStatus());
        verify(em, never()).createQuery(anyString()); verifyNoInteractions(logs);
    }
}

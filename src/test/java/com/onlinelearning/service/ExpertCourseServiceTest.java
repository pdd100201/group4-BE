package com.onlinelearning.service;

import com.onlinelearning.dto.course.CourseRequest;
import com.onlinelearning.entity.*;
import com.onlinelearning.exception.ApiException;
import com.onlinelearning.repository.*;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import jakarta.persistence.TypedQuery;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Tracking RMS rows 31-35: UC24-28. HTTP role checks are integration tests. */
@ExtendWith(MockitoExtension.class)
class ExpertCourseServiceTest {
    private static final String EMAIL = "expert@example.com";
    @Mock CourseRepository courses;
    @Mock UserRepository users;
    @Mock AuditLogRepository logs;
    @Mock EntityManager em;
    @Mock MediaService media;
    @InjectMocks ExpertCourseService service;

    private Course course(CourseStatus status) {
        User owner = new User(); owner.setId(2L); owner.setEmail(EMAIL);
        Course course = new Course(); course.setId(1L); course.setExpert(owner);
        course.setTitle("Old title"); course.setDescription("Old description");
        course.setCategory("Java"); course.setPrice(BigDecimal.TEN); course.setStatus(status);
        return course;
    }

    private CourseRequest input() {
        return new CourseRequest(" Java basics ", " Learn Java ", " Java ", new BigDecimal("100000"));
    }

    // UC25 is covered by CreateCourseTest, whose UTCID01-UTCID11 match the supplied sheet.

    @ParameterizedTest
    @EnumSource(value = CourseStatus.class, names = {"DRAFT", "REJECTED"})
    void uc26UpdatesOwnedEditableCourseWithoutChangingState(CourseStatus status) {
        Course course = course(status);
        when(courses.findForUpdate(1L)).thenReturn(Optional.of(course));

        var result = service.update(1L, EMAIL, input());

        assertEquals("Java basics", result.title());
        assertEquals("Learn Java", result.description());
        assertEquals(status, result.status());
        assertAudit("COURSE_UPDATED", course.getExpert());
    }

    @ParameterizedTest
    @EnumSource(value = CourseStatus.class, names = {"PENDING_REVIEW", "APPROVED", "ARCHIVED"})
    void uc26RejectsLockedCourseBeforeChangingFields(CourseStatus status) {
        Course course = course(status);
        when(courses.findForUpdate(1L)).thenReturn(Optional.of(course));
        assertEquals(HttpStatus.CONFLICT, assertThrows(ApiException.class,
                () -> service.update(1L, EMAIL, input())).status());
        assertEquals("Old title", course.getTitle());
        verifyNoInteractions(logs, media);
    }

    @Test
    void uc26RejectsAnotherExpertsCourse() {
        when(courses.findForUpdate(1L)).thenReturn(Optional.of(course(CourseStatus.DRAFT)));
        assertEquals(HttpStatus.FORBIDDEN, assertThrows(ApiException.class,
                () -> service.update(1L, "other@example.com", input())).status());
        verifyNoInteractions(logs, media);
    }

    @Test
    void uc24RejectsReadingAnotherExpertsCourse() {
        when(courses.findById(1L)).thenReturn(Optional.of(course(CourseStatus.DRAFT)));
        assertEquals(HttpStatus.FORBIDDEN, assertThrows(ApiException.class,
                () -> service.get(1L, "other@example.com")).status());
    }

    @Test
    void uc24ReturnsOwnedCourseAndRejectionReason() {
        Course course = course(CourseStatus.REJECTED); course.setRejectionReason("Add lessons");
        when(courses.findById(1L)).thenReturn(Optional.of(course));
        var result = service.get(1L, EMAIL);
        assertEquals("Add lessons", result.rejectionReason());
        assertTrue(result.editable()); assertTrue(result.deletable());
    }

    @Test
    void uc24MissingCourseReturnsNotFound() {
        when(courses.findById(1L)).thenReturn(Optional.empty());
        assertEquals(HttpStatus.NOT_FOUND, assertThrows(ApiException.class,
                () -> service.get(1L, EMAIL)).status());
    }

    @ParameterizedTest
    @EnumSource(value = CourseStatus.class, names = {"PENDING_REVIEW", "PUBLISHED", "APPROVED", "ARCHIVED"})
    void uc28RejectsExpertDeleteInProtectedStates(CourseStatus status) {
        when(courses.findForUpdate(1L)).thenReturn(Optional.of(course(status)));
        assertEquals(HttpStatus.CONFLICT, assertThrows(ApiException.class,
                () -> service.delete(1L, EMAIL)).status());
        verifyNoInteractions(em, logs);
    }

    @Test
    void uc28RejectsAnotherExpertsDelete() {
        when(courses.findForUpdate(1L)).thenReturn(Optional.of(course(CourseStatus.DRAFT)));
        assertEquals(HttpStatus.FORBIDDEN, assertThrows(ApiException.class,
                () -> service.delete(1L, "other@example.com")).status());
        verifyNoInteractions(em, logs);
    }

    @Test
    void uc28RetainedRegistrationPreventsHardDelete() {
        when(courses.findForUpdate(1L)).thenReturn(Optional.of(course(CourseStatus.DRAFT)));
        TypedQuery<Long> count = mock(TypedQuery.class);
        when(em.createQuery("select count(e) from CourseRegistration e where e.course.id = :id", Long.class)).thenReturn(count);
        when(count.setParameter("id", 1L)).thenReturn(count);
        when(count.getSingleResult()).thenReturn(1L);
        assertEquals(HttpStatus.CONFLICT, assertThrows(ApiException.class,
                () -> service.delete(1L, EMAIL)).status());
        verifyNoInteractions(logs);
        verify(em, never()).createQuery(anyString());
    }

    @ParameterizedTest
    @EnumSource(value = CourseStatus.class, names = {"DRAFT", "REJECTED"})
    void uc28DeletesEmptyCourseAndDetachesMedia(CourseStatus status) {
        Course course = course(status);
        when(courses.findForUpdate(1L)).thenReturn(Optional.of(course));
        TypedQuery<Long> count = mock(TypedQuery.class);
        when(em.createQuery(startsWith("select count(e)"), eq(Long.class))).thenReturn(count);
        when(count.setParameter("id", 1L)).thenReturn(count);
        when(count.getSingleResult()).thenReturn(0L);
        MediaAsset asset = new MediaAsset(); asset.setCourse(course);
        TypedQuery<MediaAsset> assets = mock(TypedQuery.class);
        when(em.createQuery(anyString(), eq(MediaAsset.class))).thenReturn(assets);
        when(assets.setParameter("id", 1L)).thenReturn(assets);
        when(assets.getResultList()).thenReturn(List.of(asset));
        Query deletion = mock(Query.class);
        when(em.createQuery(anyString())).thenReturn(deletion);
        when(deletion.setParameter("id", 1L)).thenReturn(deletion);

        service.delete(1L, EMAIL);

        assertNull(asset.getCourse());
        verify(em).createQuery("delete from Course c where c.id=:id");
        verify(deletion, times(2)).executeUpdate();
        assertAudit("COURSE_DELETED", course.getExpert());
    }

    private void assertAudit(String action, User actor) {
        ArgumentCaptor<AuditLog> audit = ArgumentCaptor.forClass(AuditLog.class);
        verify(logs).save(audit.capture());
        assertEquals(action, audit.getValue().getAction());
        assertEquals("1", audit.getValue().getTargetId());
        assertSame(actor, audit.getValue().getActor());
        assertNotNull(audit.getValue().getOccurredAt());
    }
}

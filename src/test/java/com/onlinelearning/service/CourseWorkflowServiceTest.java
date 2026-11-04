package com.onlinelearning.service;

import com.onlinelearning.entity.*;
import com.onlinelearning.exception.ApiException;
import com.onlinelearning.repository.*;
import com.onlinelearning.service.impl.CourseServiceImpl;
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

/** Tracking RMS rows 41-44: UC66-69. */
@ExtendWith(MockitoExtension.class)
class CourseWorkflowServiceTest {
    @Mock CourseRepository courses;
    @Mock UserRepository users;
    @Mock AuditLogRepository logs;
    @Mock CourseSubmissionReadinessService readiness;
    @org.mockito.Mock com.onlinelearning.service.NotificationEvents notificationEvents;
    @InjectMocks CourseServiceImpl service;

    private Course course(CourseStatus status) {
        User expert = new User(); expert.setEmail("expert@example.com");
        Course course = new Course(); course.setId(1L); course.setExpert(expert); course.setStatus(status);
        when(courses.findForUpdate(1L)).thenReturn(Optional.of(course));
        return course;
    }

    private User actor(String role) {
        User actor = new User(); actor.setEmail("actor@example.com");
        Role assigned = new Role(); assigned.setCode(role); actor.getRoles().add(assigned);
        when(users.findByEmailIgnoreCase(actor.getEmail())).thenReturn(Optional.of(actor));
        return actor;
    }

    @Test
    void uc66SubmitsReadyDraftAndRecordsAudit() {
        Course course = course(CourseStatus.DRAFT);
        assertSame(course, service.submit(1L, "expert@example.com"));
        assertEquals(CourseStatus.PENDING_REVIEW, course.getStatus());
        verify(readiness).requireReady(course);
        assertAudit("COURSE_SUBMITTED");
    }

    @Test
    void uc69ResubmitsReadyRejectedCourse() {
        Course course = course(CourseStatus.REJECTED);
        service.resubmit(1L, "expert@example.com");
        assertEquals(CourseStatus.PENDING_REVIEW, course.getStatus());
        verify(readiness).requireReady(course);
        assertAudit("COURSE_RESUBMITTED");
    }

    @ParameterizedTest
    @EnumSource(value=CourseStatus.class, names="DRAFT", mode=EnumSource.Mode.EXCLUDE)
    void uc66RejectsSubmitFromEveryNonDraftState(CourseStatus status) {
        Course course = course(status);
        assertEquals(HttpStatus.CONFLICT, assertThrows(ApiException.class,
                () -> service.submit(1L, "expert@example.com")).status());
        assertEquals(status, course.getStatus()); verifyNoInteractions(readiness, logs);
    }

    @ParameterizedTest
    @EnumSource(value=CourseStatus.class, names="REJECTED", mode=EnumSource.Mode.EXCLUDE)
    void uc69RejectsResubmitFromEveryNonRejectedState(CourseStatus status) {
        course(status);
        assertEquals(HttpStatus.CONFLICT, assertThrows(ApiException.class,
                () -> service.resubmit(1L, "expert@example.com")).status());
        verifyNoInteractions(readiness, logs);
    }

    @ParameterizedTest
    @ValueSource(booleans={false,true})
    void uc66And69RejectIncompleteContentWithoutChangingState(boolean resubmit) {
        CourseStatus status = resubmit ? CourseStatus.REJECTED : CourseStatus.DRAFT;
        Course course = course(status);
        doThrow(new ApiException(HttpStatus.BAD_REQUEST, "Missing lessons")).when(readiness).requireReady(course);
        assertThrows(ApiException.class, () -> {
            if (resubmit) service.resubmit(1L, "expert@example.com");
            else service.submit(1L, "expert@example.com");
        });
        assertEquals(status, course.getStatus()); verifyNoInteractions(logs);
    }

    @Test
    void uc66RejectsWrongOwner() {
        course(CourseStatus.DRAFT);
        assertEquals(HttpStatus.FORBIDDEN, assertThrows(ApiException.class,
                () -> service.submit(1L, "other@example.com")).status());
        verifyNoInteractions(readiness, logs);
    }

    @Test
    void uc67ApprovesDirectlyToPublishedAndClearsOldReason() {
        User manager = actor("MANAGER"); Course course = course(CourseStatus.PENDING_REVIEW);
        course.setRejectionReason("Old rejection");
        service.approve(1L, manager.getEmail());
        assertEquals(CourseStatus.PUBLISHED, course.getStatus());
        assertSame(manager, course.getReviewedBy()); assertNull(course.getRejectionReason());
        assertAudit("COURSE_APPROVED");
        assertEquals(HttpStatus.CONFLICT, assertThrows(ApiException.class,
                () -> service.approve(1L, manager.getEmail())).status());
        verify(logs, times(1)).save(any(AuditLog.class));
    }

    @ParameterizedTest
    @EnumSource(value=CourseStatus.class, names="PENDING_REVIEW", mode=EnumSource.Mode.EXCLUDE)
    void uc67RejectsApprovalOfNonPendingCourse(CourseStatus status) {
        actor("MANAGER"); course(status);
        assertEquals(HttpStatus.CONFLICT, assertThrows(ApiException.class,
                () -> service.approve(1L, "actor@example.com")).status());
        verifyNoInteractions(logs);
    }

    @ParameterizedTest
    @ValueSource(strings={"STUDENT","EXPERT","ADMIN"})
    void uc67And68OnlyManagerCanReview(String role) {
        actor(role);
        assertEquals(HttpStatus.FORBIDDEN, assertThrows(ApiException.class,
                () -> service.approve(1L, "actor@example.com")).status());
        assertEquals(HttpStatus.FORBIDDEN, assertThrows(ApiException.class,
                () -> service.reject(1L, "actor@example.com", "Missing video")).status());
        verifyNoInteractions(courses, logs);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings={" ","   "})
    void uc68RejectsMissingReason(String reason) {
        actor("MANAGER");
        assertEquals(HttpStatus.BAD_REQUEST, assertThrows(ApiException.class,
                () -> service.reject(1L, "actor@example.com", reason)).status());
        verifyNoInteractions(courses, logs);
    }

    @Test
    void uc68RejectsReasonOver255Characters() {
        actor("MANAGER");
        assertEquals(HttpStatus.BAD_REQUEST, assertThrows(ApiException.class,
                () -> service.reject(1L, "actor@example.com", "a".repeat(256))).status());
        verifyNoInteractions(courses, logs);
    }

    @Test
    void uc68Accepts255CharacterReason() {
        actor("MANAGER"); Course course = course(CourseStatus.PENDING_REVIEW);
        service.reject(1L, "actor@example.com", "a".repeat(255));
        assertEquals(CourseStatus.REJECTED, course.getStatus());
        assertEquals(255, course.getRejectionReason().length()); assertAudit("COURSE_REJECTED");
    }

    @Test
    void uc68StoresTrimmedReasonAndReviewer() {
        User manager = actor("MANAGER"); Course course = course(CourseStatus.PENDING_REVIEW);
        service.reject(1L, manager.getEmail(), " Missing video ");
        assertEquals("Missing video", course.getRejectionReason());
        assertSame(manager, course.getReviewedBy()); assertAudit("COURSE_REJECTED");
    }

    private void assertAudit(String action) {
        ArgumentCaptor<AuditLog> audit = ArgumentCaptor.forClass(AuditLog.class);
        verify(logs).save(audit.capture());
        assertEquals(action, audit.getValue().getAction());
        assertEquals("1", audit.getValue().getTargetId()); assertNotNull(audit.getValue().getOccurredAt());
    }
}

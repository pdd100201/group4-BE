package com.onlinelearning.service;

import com.onlinelearning.dto.course.CourseRequest;
import com.onlinelearning.entity.*;
import com.onlinelearning.exception.ApiException;
import com.onlinelearning.repository.*;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest @ActiveProfiles("test") @AutoConfigureMockMvc @Transactional
class CourseSubmissionIntegrationTests {
    @Autowired ExpertCourseService expert;
    @Autowired CourseService workflow;
    @Autowired CourseSubmissionReadinessService readiness;
    @Autowired CourseRepository courses;
    @Autowired AuditLogRepository audit;
    @Autowired EntityManager em;
    @Autowired MockMvc mvc;
    private final String owner = "expert@onlinelearning.local";
    private Course draft() {
        Long id = expert.create(owner, new CourseRequest("Submit course", "Description", "Java", new BigDecimal("100000"))).id();
        return courses.findById(id).orElseThrow();
    }
    @Test void emptyCourseCannotSubmitAndChecklistExplainsMissingData() throws Exception {
        Course course = draft();
        mvc.perform(get("/api/v1/expert/courses/" + course.getId() + "/submission-readiness").with(user(owner).roles("EXPERT")))
            .andExpect(status().isOk()).andExpect(jsonPath("ready").value(false));
        mvc.perform(post("/api/v1/expert/courses/" + course.getId() + "/submit").with(user(owner).roles("EXPERT")))
            .andExpect(status().isBadRequest());
        assertEquals(CourseStatus.DRAFT, course.getStatus());
    }
    @Test void oneCompleteLessonAndActiveQuizAreEnoughAndRecordAudit() throws Exception {
        Course course = draft(); ReadyCourseFixture.populate(em, course);
        assertTrue(readiness.get(course.getId(), owner).ready());
        mvc.perform(post("/api/v1/expert/courses/" + course.getId() + "/submit").with(user(owner).roles("EXPERT")))
            .andExpect(status().isOk()).andExpect(jsonPath("status").value("PENDING_REVIEW"));
        assertFalse(expert.get(course.getId(), owner).editable());
        assertTrue(audit.findAll().stream().anyMatch(l -> "COURSE_SUBMITTED".equals(l.getAction()) && course.getId().toString().equals(l.getTargetId())));
        assertEquals(409, assertThrows(ApiException.class, () -> workflow.submit(course.getId(), owner)).status().value());
        workflow.reject(course.getId(), "manager@onlinelearning.local", "Revise description");
        assertEquals(409, assertThrows(ApiException.class, () -> workflow.submit(course.getId(), owner)).status().value());
        assertEquals(CourseStatus.PENDING_REVIEW, workflow.resubmit(course.getId(), owner).getStatus());
    }
    @Test void missingVideoInactiveLessonAndInactiveQuizBlockSubmit() {
        Course course = draft(); ReadyCourseFixture.populate(em, course);
        Lesson lesson = em.createQuery("select l from Lesson l where l.course.id=:id", Lesson.class).setParameter("id", course.getId()).getSingleResult();
        var video = lesson.getVideo(); lesson.setVideo(null);
        assertFalse(readiness.get(course.getId(), owner).ready());
        assertEquals(400, assertThrows(ApiException.class, () -> workflow.submit(course.getId(), owner)).status().value());
        lesson.setVideo(video); lesson.setActive(false); assertFalse(readiness.get(course.getId(), owner).ready());
        lesson.setActive(true);
        Quiz quiz = em.createQuery("select q from Quiz q where q.course.id=:id", Quiz.class).setParameter("id", course.getId()).getSingleResult();
        quiz.setActive(false); assertFalse(readiness.get(course.getId(), owner).ready());
        quiz.setActive(true); quiz.setDurationMinutes(0); assertFalse(readiness.get(course.getId(), owner).ready());
    }
    @Test void readinessAndSubmitEnforceRoleOwnerAndState() throws Exception {
        Course course = draft(); String path = "/api/v1/expert/courses/" + course.getId();
        for (String role : new String[]{"ADMIN", "MANAGER", "STUDENT"}) {
            mvc.perform(get(path + "/submission-readiness").with(user("other@test.local").roles(role))).andExpect(status().isForbidden());
            mvc.perform(post(path + "/submit").with(user("other@test.local").roles(role))).andExpect(status().isForbidden());
        }
        assertEquals(403, assertThrows(ApiException.class, () -> readiness.get(course.getId(), "student@onlinelearning.local")).status().value());
        mvc.perform(get(path + "/submission-readiness")).andExpect(status().isUnauthorized());
        course.setStatus(CourseStatus.PUBLISHED);
        assertFalse(readiness.get(course.getId(), owner).eligible());
        assertEquals(409, assertThrows(ApiException.class, () -> workflow.submit(course.getId(), owner)).status().value());
    }
}

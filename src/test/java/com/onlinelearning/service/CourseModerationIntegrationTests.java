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
class CourseModerationIntegrationTests {
    @Autowired ExpertCourseService expert;
    @Autowired CourseModerationService moderation;
    @Autowired CourseRepository courses;
    @Autowired UserRepository users;
    @Autowired AuditLogRepository logs;
    @Autowired EntityManager em;
    @Autowired MockMvc mvc;
    private Course published() {
        Long id = expert.create("expert@onlinelearning.local", new CourseRequest("Moderation", "Description", "Java", BigDecimal.ZERO)).id();
        Course c = courses.findById(id).orElseThrow(); c.setStatus(CourseStatus.PUBLISHED); return c;
    }
    @Test void bothRolesArchiveWithReasonAndKeepRegistration() throws Exception {
        for (String role : new String[]{"ADMIN", "MANAGER"}) {
            Course c = published(); CourseRegistration r = new CourseRegistration(); r.setCourse(c);
            r.setStudent(users.findByEmailIgnoreCase("student@onlinelearning.local").orElseThrow()); em.persist(r);
            mvc.perform(post("/api/v1/" + role.toLowerCase() + "/courses/" + c.getId() + "/archive")
                .with(user(role.toLowerCase() + "@onlinelearning.local").roles(role)).contentType("application/json")
                .content("{\"reason\":\"  Copyright concern  \"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("status").value("ARCHIVED"));
            assertNotNull(em.find(CourseRegistration.class, r.getId()));
            assertTrue(logs.findAll().stream().anyMatch(l -> "COURSE_ARCHIVED".equals(l.getAction()) && "Copyright concern".equals(l.getMetadata())));
        }
        mvc.perform(get("/api/v1/public/courses")).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
    }
    @Test void publishedCourseWithUnreferencedContentCanBeDeletedAndAudited() throws Exception {
        for (String role : new String[]{"ADMIN", "MANAGER"}) {
            Course c = published(); Long id = c.getId();
            Lesson lesson = new Lesson(); lesson.setCourse(c); lesson.setTitle("Lesson"); em.persist(lesson);
            Question question = new Question(); question.setCourse(c); question.setContent("Question"); em.persist(question);
            QuestionOption option = new QuestionOption(); option.setQuestion(question); option.setContent("Answer"); em.persist(option);
            Quiz quiz = new Quiz(); quiz.setCourse(c); quiz.setTitle("Quiz"); em.persist(quiz);
            QuizQuestion link = new QuizQuestion(); link.setQuiz(quiz); link.setQuestion(question); em.persist(link); em.flush();
            mvc.perform(delete("/api/v1/" + role.toLowerCase() + "/courses/" + id)
                .with(user(role.toLowerCase() + "@onlinelearning.local").roles(role)).contentType("application/json")
                .content("{\"reason\":\"Violation confirmed\"}"))
                .andDo(result -> { if (result.getResolvedException() != null) throw result.getResolvedException(); })
                .andExpect(status().isNoContent());
            em.clear(); assertFalse(courses.existsById(id)); assertNull(em.find(Lesson.class, lesson.getId()));
            assertNull(em.find(Quiz.class, quiz.getId())); assertNull(em.find(Question.class, question.getId()));
            assertTrue(logs.findAll().stream().anyMatch(l -> "COURSE_DELETED".equals(l.getAction()) && id.toString().equals(l.getTargetId()) && "Violation confirmed".equals(l.getMetadata())));
        }
    }
    @Test void retainedDataBlocksDeleteWithoutChangingState() {
        for (String retention : new String[]{"registration", "attempt", "progress"}) {
            Course c = published(); User student = users.findByEmailIgnoreCase("student@onlinelearning.local").orElseThrow();
            if (retention.equals("registration")) { CourseRegistration r = new CourseRegistration(); r.setCourse(c); r.setStudent(student); em.persist(r); }
            if (retention.equals("attempt")) { Quiz q = new Quiz(); q.setCourse(c); q.setTitle("Quiz"); em.persist(q); QuizAttempt a = new QuizAttempt(); a.setQuiz(q); a.setStudent(student); em.persist(a); }
            if (retention.equals("progress")) { Lesson l = new Lesson(); l.setCourse(c); l.setTitle("Lesson"); em.persist(l); CourseRegistration r = new CourseRegistration(); r.setCourse(c); r.setStudent(student); em.persist(r); StudentLessonProgress p = new StudentLessonProgress(); p.setLesson(l); p.setStudent(student); p.setRegistration(r); em.persist(p); }
            assertEquals(409, assertThrows(ApiException.class, () -> moderation.delete(c.getId(), "manager@onlinelearning.local", "Violation")).status().value());
            assertEquals(CourseStatus.PUBLISHED, c.getStatus()); assertTrue(courses.existsById(c.getId()));
        }
    }
    @Test void validatesReasonRoleStateAndMissingCourse() throws Exception {
        Course c = published(); String path = "/api/v1/manager/courses/" + c.getId();
        for (String reason : new String[]{"", "   ", "x".repeat(256)}) {
            String body = "{\"reason\":\"" + reason + "\"}";
            mvc.perform(post(path + "/archive").with(user("manager@onlinelearning.local").roles("MANAGER")).contentType("application/json").content(body)).andExpect(status().isBadRequest());
            mvc.perform(delete(path).with(user("manager@onlinelearning.local").roles("MANAGER")).contentType("application/json").content(body)).andExpect(status().isBadRequest());
        }
        for (String role : new String[]{"STUDENT", "EXPERT"}) {
            mvc.perform(post(path + "/archive").with(user("other@test.local").roles(role)).contentType("application/json").content("{\"reason\":\"Reason\"}")).andExpect(status().isForbidden());
            mvc.perform(delete(path).with(user("other@test.local").roles(role)).contentType("application/json").content("{\"reason\":\"Reason\"}")).andExpect(status().isForbidden());
        }
        assertEquals(403, assertThrows(ApiException.class, () -> moderation.archive(c.getId(), "expert@onlinelearning.local", "Reason")).status().value());
        c.setStatus(CourseStatus.DRAFT);
        assertEquals(409, assertThrows(ApiException.class, () -> moderation.archive(c.getId(), "manager@onlinelearning.local", "Reason")).status().value());
        c.setStatus(CourseStatus.PENDING_REVIEW);
        assertEquals(409, assertThrows(ApiException.class, () -> moderation.delete(c.getId(), "manager@onlinelearning.local", "Reason")).status().value());
        mvc.perform(delete("/api/v1/admin/courses/" + Long.MAX_VALUE).with(user("admin@onlinelearning.local").roles("ADMIN")).contentType("application/json").content("{\"reason\":\"Reason\"}")).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/admin/courses").with(user("student@onlinelearning.local").roles("STUDENT"))).andExpect(status().isForbidden());
    }
}

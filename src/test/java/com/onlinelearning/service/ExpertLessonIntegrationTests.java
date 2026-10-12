package com.onlinelearning.service;
import com.onlinelearning.dto.course.*;
import com.onlinelearning.entity.*;
import com.onlinelearning.exception.ApiException;
import com.onlinelearning.repository.*;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.test.web.servlet.MockMvc;
import java.math.BigDecimal;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest @ActiveProfiles("test") @Transactional @AutoConfigureMockMvc
class ExpertLessonIntegrationTests {
    @Autowired ExpertCourseService courses;
    @Autowired ExpertLessonService lessons;
    @Autowired CourseRepository repository;
    @Autowired UserRepository users;
    @Autowired AuditLogRepository audit;
    @Autowired EntityManager em;
    @Autowired MockMvc mvc;
    static final String OWNER = "expert@onlinelearning.local";
    Long course() { return courses.create(OWNER, new CourseRequest("Course", "Description", "Java", BigDecimal.ZERO)).id(); }
    LessonRequest input(int position) { return new LessonRequest("Lesson", "Content", position, true); }
    @Test void createsAndUpdatesRequiredTimestampsWithoutChangingCreationTime() {
        Long id = course(); var saved = lessons.create(id, OWNER, input(1)); em.flush(); em.clear();
        Lesson before = em.find(Lesson.class, saved.id());
        var createdAt = before.getCreatedAt(); var updatedAt = before.getUpdatedAt();
        assertNotNull(createdAt); assertNotNull(updatedAt);
        lessons.update(id, saved.id(), OWNER, new LessonRequest("Timestamp update", "Updated content", 1, true));
        em.flush(); em.clear();
        Lesson after = em.find(Lesson.class, saved.id());
        assertEquals(createdAt, after.getCreatedAt());
        assertFalse(after.getUpdatedAt().isBefore(updatedAt));
        assertEquals("Timestamp update", after.getTitle());
    }
    @Test void crudOrderingVisibilityAndAudit() {
        Long id = course(); var second = lessons.create(id, OWNER, input(2)); var first = lessons.create(id, OWNER, input(1));
        assertEquals(first.id(), lessons.list(id, OWNER).getFirst().id());
        var updated = lessons.update(id, first.id(), OWNER, new LessonRequest("Updated", "New content", 3, false));
        assertFalse(updated.active()); assertEquals("New content", lessons.get(id, first.id(), OWNER).content());
        assertEquals(second.id(), lessons.list(id, OWNER).getFirst().id());
        lessons.delete(id, first.id(), OWNER); assertEquals(1, lessons.list(id, OWNER).size());
        assertTrue(audit.findAll().stream().anyMatch(l -> "LESSON_DELETED".equals(l.getAction()) && first.id().toString().equals(l.getTargetId())));
    }
    @Test void wrongOwnerAndCrossCourseIdsAreDenied() {
        Long id = course(); Long other = course(); var l = lessons.create(id, OWNER, input(1));
        assertEquals(403, assertThrows(ApiException.class, () -> lessons.list(id, "student@onlinelearning.local")).status().value());
        assertEquals(403, assertThrows(ApiException.class, () -> lessons.create(id, "student@onlinelearning.local", input(2))).status().value());
        assertThrows(ApiException.class, () -> lessons.update(id, l.id(), "student@onlinelearning.local", input(2)));
        assertThrows(ApiException.class, () -> lessons.delete(id, l.id(), "student@onlinelearning.local"));
        assertEquals(404, assertThrows(ApiException.class, () -> lessons.get(other, l.id(), OWNER)).status().value());
        assertEquals(404, assertThrows(ApiException.class, () -> lessons.update(other, l.id(), OWNER, input(1))).status().value());
        assertEquals(404, assertThrows(ApiException.class, () -> lessons.delete(other, l.id(), OWNER)).status().value());
    }
    @Test void workflowLocksAllMutationsButAllowsReading() {
        Long id = course(); var l = lessons.create(id, OWNER, input(1));
        for (CourseStatus status : new CourseStatus[]{CourseStatus.PENDING_REVIEW, CourseStatus.PUBLISHED, CourseStatus.ARCHIVED}) {
            repository.findById(id).orElseThrow().setStatus(status);
            assertEquals(1, lessons.list(id, OWNER).size());
            assertEquals(409, assertThrows(ApiException.class, () -> lessons.create(id, OWNER, input(2))).status().value());
            assertThrows(ApiException.class, () -> lessons.update(id, l.id(), OWNER, input(1)));
            assertThrows(ApiException.class, () -> lessons.delete(id, l.id(), OWNER));
        }
        repository.findById(id).orElseThrow().setStatus(CourseStatus.REJECTED);
        assertEquals("Lesson", lessons.update(id, l.id(), OWNER, input(1)).title());
    }
    @Test void retainedProgressAndDuplicatePositionAreProtected() {
        Long id = course(); var l = lessons.create(id, OWNER, input(1));
        assertEquals(409, assertThrows(ApiException.class, () -> lessons.create(id, OWNER, input(1))).status().value());
        StudentLessonProgress progress = new StudentLessonProgress();
        progress.setStudent(users.findByEmailIgnoreCase("student@onlinelearning.local").orElseThrow());
        progress.setLesson(em.find(Lesson.class, l.id())); em.persist(progress); em.flush();
        assertEquals(409, assertThrows(ApiException.class, () -> lessons.delete(id, l.id(), OWNER)).status().value());
        assertEquals(l.id(), lessons.get(id, l.id(), OWNER).id());
    }
    @Test void endpointsEnforceRolesValidateAndPersist() throws Exception {
        Long id = course(); String path = "/api/v1/expert/courses/" + id + "/lessons";
        mvc.perform(get(path)).andExpect(status().isUnauthorized());
        for (String role : new String[]{"ADMIN", "MANAGER", "STUDENT"}) {
            mvc.perform(get(path).with(user("other@test.local").roles(role))).andExpect(status().isForbidden());
            mvc.perform(post(path).with(user("other@test.local").roles(role)).contentType("application/json").content("{}")) .andExpect(status().isForbidden());
            mvc.perform(put(path + "/1").with(user("other@test.local").roles(role)).contentType("application/json").content("{}")) .andExpect(status().isForbidden());
            mvc.perform(delete(path + "/1").with(user("other@test.local").roles(role))).andExpect(status().isForbidden());
        }
        mvc.perform(post(path).with(user(OWNER).roles("EXPERT")).contentType("application/json").content("{\"title\":\"\",\"content\":\"\",\"position\":0,\"active\":true}")) .andExpect(status().isBadRequest());
        String body = "{\"title\":\"HTTP lesson\",\"content\":\"Content\",\"position\":1,\"active\":true}";
        mvc.perform(post(path).with(user(OWNER).roles("EXPERT")).contentType("application/json").content(body)).andExpect(status().isCreated()).andExpect(jsonPath("title").value("HTTP lesson"));
        Long lessonId = lessons.list(id, OWNER).getFirst().id();
        mvc.perform(put(path + "/" + lessonId).with(user(OWNER).roles("EXPERT")).contentType("application/json").content(body.replace("HTTP lesson", "Updated"))).andExpect(status().isOk()).andExpect(jsonPath("title").value("Updated"));
        mvc.perform(delete(path + "/" + lessonId).with(user(OWNER).roles("EXPERT"))).andExpect(status().isNoContent());
        mvc.perform(get(path + "/" + lessonId).with(user(OWNER).roles("EXPERT"))).andExpect(status().isNotFound());
    }
}

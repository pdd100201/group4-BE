package com.onlinelearning.service;

import com.onlinelearning.dto.course.CourseRequest;
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
class ExpertCourseIntegrationTests {
    @Autowired ExpertCourseService expert;
    @Autowired CourseService workflow;
    @Autowired CourseRepository courses;
    @Autowired UserRepository users;
    @Autowired EntityManager em;
    @Autowired MockMvc mvc;
    private static final String OWNER = "expert@onlinelearning.local";
    private CourseRequest input(String title) { return new CourseRequest(title, "Nội dung khóa học", "Java", new BigDecimal("100000")); }
    @Test void publishedCourseCanBeEditedImmediatelyButNotDeletedByExpert() throws Exception {
        var created = expert.create(OWNER, input("Published original"));
        var course = courses.findById(created.id()).orElseThrow(); course.setStatus(CourseStatus.PUBLISHED);
        mvc.perform(put("/api/v1/expert/courses/" + created.id()).with(user(OWNER).roles("EXPERT"))
            .contentType("application/json").content("{\"title\":\"Published updated\",\"description\":\"Updated description\",\"category\":\"Java\",\"price\":450000}"))
            .andExpect(status().isOk()).andExpect(jsonPath("status").value("PUBLISHED"))
            .andExpect(jsonPath("editable").value(true)).andExpect(jsonPath("deletable").value(false));
        mvc.perform(get("/api/v1/public/courses/" + created.id())).andExpect(status().isOk())
            .andExpect(jsonPath("title").value("Published updated")).andExpect(jsonPath("price").value(450000));
        mvc.perform(delete("/api/v1/expert/courses/" + created.id()).with(user(OWNER).roles("EXPERT")))
            .andExpect(status().isConflict());
        assertEquals(403, assertThrows(ApiException.class, () -> expert.update(created.id(), "student@onlinelearning.local", input("Attack"))).status().value());
    }
    @Test void archivedAndPendingCoursesStillCannotBeEdited() {
        var created = expert.create(OWNER, input("Locked"));
        for (CourseStatus state : new CourseStatus[]{CourseStatus.ARCHIVED, CourseStatus.PENDING_REVIEW, CourseStatus.APPROVED}) {
            courses.findById(created.id()).orElseThrow().setStatus(state);
            assertFalse(expert.get(created.id(), OWNER).editable());
            assertEquals(409, assertThrows(ApiException.class, () -> expert.update(created.id(), OWNER, input("No"))).status().value());
        }
    }
    @Test void crudAndFilteringAreScopedToOwner() {
        var created = expert.create(OWNER, input("Java cơ bản"));
        assertEquals(CourseStatus.DRAFT, created.status());
        assertEquals("Java cơ bản", expert.get(created.id(), OWNER).title());
        assertEquals(1, expert.list(OWNER, "Java cơ bản", "Java", CourseStatus.DRAFT, 0, 10).getTotalElements());
        assertEquals(0, expert.list("student@onlinelearning.local", "Java cơ bản", "", null, 0, 10).getTotalElements());
        assertEquals("Java nâng cao", expert.update(created.id(), OWNER, input("Java nâng cao")).title());
        expert.delete(created.id(), OWNER);
        assertFalse(courses.existsById(created.id()));
    }
    @Test void wrongOwnerAndMissingCourseAreDenied() {
        var c = expert.create(OWNER, input("Owned"));
        for (Runnable action : new Runnable[]{() -> expert.get(c.id(), "student@onlinelearning.local"), () -> expert.update(c.id(), "student@onlinelearning.local", input("Attack")), () -> expert.delete(c.id(), "student@onlinelearning.local")})
            assertEquals(403, assertThrows(ApiException.class, action::run).status().value());
        assertEquals(404, assertThrows(ApiException.class, () -> expert.get(Long.MAX_VALUE, OWNER)).status().value());
    }
    @Test void reviewLocksEditingAndPublishesOnApproval() {
        var c = expert.create(OWNER, input("Workflow"));
        ReadyCourseFixture.populate(em, courses.findById(c.id()).orElseThrow());
        assertThrows(ApiException.class, () -> workflow.resubmit(c.id(), OWNER));
        workflow.submit(c.id(), OWNER);
        assertEquals(409, assertThrows(ApiException.class, () -> expert.update(c.id(), OWNER, input("Locked"))).status().value());
        assertThrows(ApiException.class, () -> expert.delete(c.id(), OWNER));
        assertThrows(ApiException.class, () -> workflow.submit(c.id(), OWNER));
        workflow.reject(c.id(), "manager@onlinelearning.local", "Bổ sung nội dung");
        expert.update(c.id(), OWNER, new CourseRequest("Updated", "Nội dung khóa học", "Java", new BigDecimal("100000"), courses.findById(c.id()).orElseThrow().getThumbnail().getId()));
        assertThrows(ApiException.class, () -> workflow.submit(c.id(), OWNER));
        workflow.resubmit(c.id(), OWNER);
        assertEquals(CourseStatus.PUBLISHED, workflow.approve(c.id(), "manager@onlinelearning.local").getStatus());
        assertThrows(ApiException.class, () -> expert.delete(c.id(), OWNER));
    }
    @Test void retainedRegistrationPreventsDeletion() {
        var c = expert.create(OWNER, input("Retained"));
        CourseRegistration registration = new CourseRegistration();
        registration.setCourse(courses.findById(c.id()).orElseThrow());
        registration.setStudent(users.findByEmailIgnoreCase("student@onlinelearning.local").orElseThrow());
        em.persist(registration); em.flush();
        assertEquals(409, assertThrows(ApiException.class, () -> expert.delete(c.id(), OWNER)).status().value());
        assertTrue(courses.existsById(c.id()));
    }
    @Test void retainedContentPreventsDeletion() {
        var c = expert.create(OWNER, input("Content"));
        Lesson lesson = new Lesson(); lesson.setTitle("Bài học"); lesson.setCourse(courses.findById(c.id()).orElseThrow()); em.persist(lesson); em.flush();
        assertEquals(409, assertThrows(ApiException.class, () -> expert.delete(c.id(), OWNER)).status().value());
    }
    @Test void endpointsEnforceRoleAndValidation() throws Exception {
        mvc.perform(get("/api/v1/expert/courses")).andExpect(status().isUnauthorized());
        for (String role : new String[]{"STUDENT", "MANAGER", "ADMIN"}) {
            mvc.perform(get("/api/v1/expert/courses").with(user("other@test.local").roles(role))).andExpect(status().isForbidden());
            mvc.perform(post("/api/v1/expert/courses").with(user("other@test.local").roles(role)).contentType("application/json").content("{}")) .andExpect(status().isForbidden());
            mvc.perform(put("/api/v1/expert/courses/1").with(user("other@test.local").roles(role)).contentType("application/json").content("{}")) .andExpect(status().isForbidden());
            mvc.perform(delete("/api/v1/expert/courses/1").with(user("other@test.local").roles(role))).andExpect(status().isForbidden());
        }
        mvc.perform(post("/api/v1/expert/courses").with(user(OWNER).roles("EXPERT")).contentType("application/json").content("{\"title\":\"\",\"description\":\"Desc\",\"category\":\"Java\",\"price\":-1}")) .andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/expert/courses?page=-1").with(user(OWNER).roles("EXPERT"))).andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/expert/courses?status=INVALID").with(user(OWNER).roles("EXPERT"))).andExpect(status().isBadRequest());
        var c = expert.create(OWNER, input("API"));
        mvc.perform(get("/api/v1/expert/courses/" + c.id()).with(user(OWNER).roles("EXPERT"))).andExpect(status().isOk()).andExpect(jsonPath("title").value("API"));
        mvc.perform(delete("/api/v1/expert/courses/" + c.id()).with(user("student@onlinelearning.local").roles("EXPERT"))).andExpect(status().isForbidden());
    }
    @Test void writeEndpointsReturnPersistedData() throws Exception {
        String body = "{\"title\":\"HTTP course\",\"description\":\"Description\",\"category\":\"Java\",\"price\":0}";
        mvc.perform(post("/api/v1/expert/courses").with(user(OWNER).roles("EXPERT")).contentType("application/json").content(body))
            .andExpect(status().isCreated()).andExpect(jsonPath("status").value("DRAFT")).andExpect(jsonPath("editable").value(true));
        var c = expert.list(OWNER, "HTTP course", "", null, 0, 10).getContent().getFirst();
        mvc.perform(put("/api/v1/expert/courses/" + c.id()).with(user(OWNER).roles("EXPERT")).contentType("application/json").content(body.replace("HTTP course", "HTTP updated")))
            .andExpect(status().isOk()).andExpect(jsonPath("title").value("HTTP updated"));
        mvc.perform(delete("/api/v1/expert/courses/" + c.id()).with(user(OWNER).roles("EXPERT"))).andExpect(status().isNoContent());
        mvc.perform(get("/api/v1/expert/courses/" + c.id()).with(user(OWNER).roles("EXPERT"))).andExpect(status().isNotFound());
    }
}

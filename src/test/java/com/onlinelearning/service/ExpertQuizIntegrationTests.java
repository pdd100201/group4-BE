package com.onlinelearning.service;
import com.onlinelearning.dto.course.*;
import com.onlinelearning.dto.quiz.*;
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
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest @ActiveProfiles("test") @Transactional @AutoConfigureMockMvc
class ExpertQuizIntegrationTests {
    @Autowired ExpertCourseService courses;
    @Autowired ExpertQuizService quizzes;
    @Autowired CourseRepository repository;
    @Autowired QuizRepository quizRepository;
    @Autowired QuizQuestionRepository quizQuestions;
    @Autowired UserRepository users;
    @Autowired AuditLogRepository audit;
    @Autowired EntityManager em;
    @Autowired MockMvc mvc;
    static final String OWNER = "expert@onlinelearning.local";
    static final String STUDENT = "student@onlinelearning.local";

    @Test void quizSearchIsPaginatedFilteredAndScopedToOwner() throws Exception {
        Long id = course();
        quizzes.create(id, OWNER, req("Search alpha", null, List.of()));
        quizzes.create(id, OWNER, req("Search beta", null, List.of()));
        assertEquals(2, quizzes.search(OWNER, id, "Search", true, 0, 1).getTotalElements());
        assertEquals(1, quizzes.search(OWNER, id, "Search", true, 0, 1).getNumberOfElements());
        assertEquals(1, quizzes.search(OWNER, id, "alpha", null, 0, 10).getTotalElements());
        assertEquals(0, quizzes.search(STUDENT, null, "Search", null, 0, 10).getTotalElements());
        assertEquals(2, quizzes.search(OWNER, id, "", "Java", null, 0, 10).getTotalElements());
        assertEquals(0, quizzes.search(OWNER, id, "", "SQL", null, 0, 10).getTotalElements());
        assertEquals(403, assertThrows(ApiException.class, () -> quizzes.search(STUDENT, id, "", null, 0, 10)).status().value());
        mvc.perform(get("/api/v1/expert/quizzes").with(user(STUDENT).roles("STUDENT"))).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/expert/quizzes").with(user(OWNER).roles("EXPERT")).param("page", "-1")).andExpect(status().isBadRequest());
    }

    @Test void activeQuizRequiresSettingsAndActiveQuestions() {
        Long id = course();
        assertEquals(400, assertThrows(ApiException.class, () -> quizzes.create(id, OWNER,
            new QuizRequest("Incomplete", null, null, null, true, null, List.of()))).status().value());
        Long questionId = question(id, "Inactive");
        em.find(Question.class, questionId).setActive(false);
        assertEquals(400, assertThrows(ApiException.class, () -> quizzes.create(id, OWNER,
            req("Invalid questions", null, List.of(new QuizQuestionItem(questionId, 1, 1))))).status().value());
    }

    @Test void questionPickerOnlyReturnsActiveOwnedQuestions() {
        Long id = course(); Long other = course();
        question(id, "Selectable question");
        Long hidden = question(id, "Inactive question");
        em.find(Question.class, hidden).setActive(false);
        question(other, "Foreign question");
        assertEquals(1, quizzes.questionBank(id, OWNER, "", 0, 20).getTotalElements());
        assertEquals(0, quizzes.questionBank(id, OWNER, "Foreign", 0, 20).getTotalElements());
        assertThrows(ApiException.class, () -> quizzes.questionBank(id, STUDENT, "", 0, 20));
    }

    Long course() { return courses.create(OWNER, new CourseRequest("Course", "Description", "Java", BigDecimal.ZERO)).id(); }
    Long question(Long courseId, String content) {
        Question q = new Question(); q.setCourse(em.find(Course.class, courseId)); q.setContent(content);
        q.setType("SINGLE_CHOICE"); q.setActive(true); em.persist(q); em.flush(); return q.getId();
    }
    Long lesson(Long courseId, String title) {
        Lesson l = new Lesson(); l.setCourse(em.find(Course.class, courseId)); l.setTitle(title);
        l.setContent("Content"); l.setPosition(1); l.setActive(true); em.persist(l); em.flush(); return l.getId();
    }
    QuizRequest req(String title, Long lessonId, List<QuizQuestionItem> items) {
        return new QuizRequest(title, 15, 70, 3, true, lessonId, items);
    }

    @Test void crudOrderingQuestionLinkingAndAudit() {
        Long id = course(); Long q1 = question(id, "Q1"); Long q2 = question(id, "Q2"); Long lessonId = lesson(id, "Lesson 1");
        var created = quizzes.create(id, OWNER, req("Quiz A", lessonId,
            List.of(new QuizQuestionItem(q2, 2, 2), new QuizQuestionItem(q1, 1, 1))));
        assertEquals(lessonId, created.lessonId());
        assertEquals(List.of(q1, q2), created.questions().stream().map(QuizQuestionResponse::questionId).toList());

        var second = quizzes.create(id, OWNER, req("Quiz B", null, List.of()));
        assertEquals(2, quizzes.list(id, OWNER).size());
        assertEquals(created.id(), quizzes.list(id, OWNER).getFirst().id());

        var updated = quizzes.update(id, created.id(), OWNER, req("Quiz A2", null, List.of(new QuizQuestionItem(q1, 3, 5))));
        assertEquals("Quiz A2", updated.title());
        assertNull(updated.lessonId());
        assertEquals(1, updated.questions().size());
        assertEquals(3, updated.questions().getFirst().position());

        quizzes.delete(id, second.id(), OWNER);
        assertEquals(1, quizzes.list(id, OWNER).size());
        assertTrue(audit.findAll().stream().anyMatch(l -> "QUIZ_DELETED".equals(l.getAction()) && second.id().toString().equals(l.getTargetId())));
        assertTrue(audit.findAll().stream().anyMatch(l -> "QUIZ_CREATED".equals(l.getAction()) && created.id().toString().equals(l.getTargetId())));
    }

    @Test void wrongOwnerAndCrossCourseIdsAreDenied() {
        Long id = course(); Long other = course(); var q = quizzes.create(id, OWNER, req("Q", null, List.of()));
        assertEquals(403, assertThrows(ApiException.class, () -> quizzes.list(id, STUDENT)).status().value());
        assertEquals(403, assertThrows(ApiException.class, () -> quizzes.create(id, STUDENT, req("X", null, List.of()))).status().value());
        assertThrows(ApiException.class, () -> quizzes.update(id, q.id(), STUDENT, req("X", null, List.of())));
        assertThrows(ApiException.class, () -> quizzes.delete(id, q.id(), STUDENT));
        assertEquals(404, assertThrows(ApiException.class, () -> quizzes.get(other, q.id(), OWNER)).status().value());
        assertEquals(404, assertThrows(ApiException.class, () -> quizzes.update(other, q.id(), OWNER, req("X", null, List.of()))).status().value());
        assertEquals(404, assertThrows(ApiException.class, () -> quizzes.delete(other, q.id(), OWNER)).status().value());
    }

    @Test void workflowLocksMutationsButAllowsReading() {
        Long id = course(); var q = quizzes.create(id, OWNER, req("Quiz", null, List.of()));
        for (CourseStatus status : new CourseStatus[]{CourseStatus.PENDING_REVIEW, CourseStatus.PUBLISHED, CourseStatus.ARCHIVED}) {
            repository.findById(id).orElseThrow().setStatus(status);
            assertEquals(1, quizzes.list(id, OWNER).size());
            assertEquals(409, assertThrows(ApiException.class, () -> quizzes.create(id, OWNER, req("X", null, List.of()))).status().value());
            assertThrows(ApiException.class, () -> quizzes.update(id, q.id(), OWNER, req("X", null, List.of())));
            assertThrows(ApiException.class, () -> quizzes.delete(id, q.id(), OWNER));
        }
        repository.findById(id).orElseThrow().setStatus(CourseStatus.REJECTED);
        assertEquals("Renamed", quizzes.update(id, q.id(), OWNER, req("Renamed", null, List.of())).title());
    }

    @Test void retentionBlocksEditAndDeleteOnceAttemptExists() {
        Long id = course(); var q = quizzes.create(id, OWNER, req("Quiz", null, List.of()));
        QuizAttempt attempt = new QuizAttempt();
        attempt.setQuiz(em.find(Quiz.class, q.id()));
        attempt.setStudent(users.findByEmailIgnoreCase(STUDENT).orElseThrow());
        em.persist(attempt); em.flush();
        assertEquals(409, assertThrows(ApiException.class, () -> quizzes.update(id, q.id(), OWNER, req("Changed", null, List.of()))).status().value());
        assertEquals(409, assertThrows(ApiException.class, () -> quizzes.delete(id, q.id(), OWNER)).status().value());
        assertEquals("Quiz", quizzes.get(id, q.id(), OWNER).title());
    }

    @Test void crossCourseLessonAndDuplicateLinksAreRejected() {
        Long id = course(); Long other = course();
        Long foreignLesson = lesson(other, "Foreign");
        Long q1 = question(id, "Q1"); Long foreignQuestion = question(other, "FQ");
        assertEquals(400, assertThrows(ApiException.class, () -> quizzes.create(id, OWNER, req("Q", foreignLesson, List.of()))).status().value());
        assertEquals(400, assertThrows(ApiException.class, () -> quizzes.create(id, OWNER, req("Q", null, List.of(new QuizQuestionItem(foreignQuestion, 1, 1))))).status().value());
        assertEquals(409, assertThrows(ApiException.class, () -> quizzes.create(id, OWNER, req("Q", null, List.of(new QuizQuestionItem(q1, 1, 1), new QuizQuestionItem(q1, 2, 1))))).status().value());
        assertEquals(409, assertThrows(ApiException.class, () -> quizzes.create(id, OWNER, req("Q", null, List.of(new QuizQuestionItem(q1, 1, 1), new QuizQuestionItem(question(id, "Q2"), 1, 1))))).status().value());
    }

    @Test void endpointsEnforceRolesValidateAndPersist() throws Exception {
        Long id = course(); String path = "/api/v1/expert/courses/" + id + "/quizzes";
        mvc.perform(get(path)).andExpect(status().isUnauthorized());
        for (String role : new String[]{"ADMIN", "MANAGER", "STUDENT"}) {
            mvc.perform(get(path).with(user("other@test.local").roles(role))).andExpect(status().isForbidden());
            mvc.perform(post(path).with(user("other@test.local").roles(role)).contentType("application/json").content("{}")).andExpect(status().isForbidden());
            mvc.perform(put(path + "/1").with(user("other@test.local").roles(role)).contentType("application/json").content("{}")).andExpect(status().isForbidden());
            mvc.perform(delete(path + "/1").with(user("other@test.local").roles(role))).andExpect(status().isForbidden());
        }
        mvc.perform(post(path).with(user(OWNER).roles("EXPERT")).contentType("application/json").content("{\"title\":\"\"}")).andExpect(status().isBadRequest());
        String body = "{\"title\":\"HTTP quiz\",\"durationMinutes\":20,\"passRate\":80,\"maxAttempts\":2,\"active\":true}";
        mvc.perform(post(path).with(user(OWNER).roles("EXPERT")).contentType("application/json").content(body))
            .andExpect(status().isCreated()).andExpect(jsonPath("title").value("HTTP quiz"));
        Long quizId = quizzes.list(id, OWNER).getFirst().id();
        mvc.perform(put(path + "/" + quizId).with(user(OWNER).roles("EXPERT")).contentType("application/json").content(body.replace("HTTP quiz", "Updated")))
            .andExpect(status().isOk()).andExpect(jsonPath("title").value("Updated"));
        mvc.perform(delete(path + "/" + quizId).with(user(OWNER).roles("EXPERT"))).andExpect(status().isNoContent());
        mvc.perform(get(path + "/" + quizId).with(user(OWNER).roles("EXPERT"))).andExpect(status().isNotFound());
    }
}

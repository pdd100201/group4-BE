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

@SpringBootTest @ActiveProfiles("test") @Transactional @AutoConfigureMockMvc
class ManagerCourseIntegrationTests {
    @Autowired ExpertCourseService expert;
    @Autowired ManagerCourseService manager;
    @Autowired CourseService workflow;
    @Autowired CourseRepository courses;
    @Autowired AuditLogRepository logs;
    @Autowired EntityManager em;
    @Autowired MockMvc mvc;
    private static final String OWNER = "expert@onlinelearning.local";
    private static final String MANAGER = "manager@onlinelearning.local";
    private Long draft(String title) { return expert.create(OWNER, new CourseRequest(title, "Mô tả", "Java", BigDecimal.ZERO)).id(); }
    private Long pending(String title) { Long id = draft(title); ReadyCourseFixture.populate(em, courses.findById(id).orElseThrow()); workflow.submit(id, OWNER); return id; }

    @Test void managerCanListFilterAndReadOtherExpertsCourses() throws Exception {
        Long id = pending("Review Java"); draft("Draft Java");
        mvc.perform(get("/api/v1/manager/courses").with(user(MANAGER).roles("MANAGER")))
            .andExpect(status().isOk()).andExpect(jsonPath("content.length()").value(1)).andExpect(jsonPath("content[0].id").value(id.intValue()));
        assertEquals(1, manager.list("Review Java", "Java", CourseStatus.PENDING_REVIEW, 0, 10).getTotalElements());
        assertEquals(0, manager.list("Unknown", "Java", CourseStatus.PENDING_REVIEW, 0, 10).getTotalElements());
        mvc.perform(get("/api/v1/manager/courses?status=&size=1").with(user(MANAGER).roles("MANAGER")))
            .andExpect(status().isOk()).andExpect(jsonPath("totalElements").value(2)).andExpect(jsonPath("totalPages").value(2));
        mvc.perform(get("/api/v1/manager/courses/" + Long.MAX_VALUE).with(user(MANAGER).roles("MANAGER"))).andExpect(status().isNotFound());
        for (String params : new String[]{"page=-1", "size=101", "status=INVALID", "page=text"})
            mvc.perform(get("/api/v1/manager/courses?" + params).with(user(MANAGER).roles("MANAGER"))).andExpect(status().isBadRequest());
    }
    @Test void previewIncludesLessonsQuizQuestionsAndAnswersWithoutMutating() throws Exception {
        Long id = pending("Preview"); Course course = courses.findById(id).orElseThrow();
        Lesson lesson = new Lesson(); lesson.setCourse(course); lesson.setTitle("Bài một"); lesson.setContent("Lesson content"); lesson.setPosition(1); em.persist(lesson);
        Question question = new Question(); question.setCourse(course); question.setContent("Question content"); question.setExplanation("Explanation"); em.persist(question);
        QuestionOption option = new QuestionOption(); option.setQuestion(question); option.setContent("Correct answer"); option.setCorrect(true); em.persist(option); question.getOptions().add(option);
        Quiz quiz = new Quiz(); quiz.setCourse(course); quiz.setTitle("Quiz"); quiz.setDurationMinutes(15); em.persist(quiz);
        QuizQuestion link = new QuizQuestion(); link.setQuiz(quiz); link.setQuestion(question); link.setPosition(1); em.persist(link); quiz.getQuizQuestions().add(link); em.flush();
        mvc.perform(get("/api/v1/manager/courses/" + id).with(user(MANAGER).roles("MANAGER")))
            .andExpect(status().isOk()).andExpect(jsonPath("lessons[0].content").value("Lesson content"))
            .andExpect(jsonPath("quizzes[1].questions[0].options[0].correct").value(true));
        assertEquals(CourseStatus.PENDING_REVIEW, courses.findById(id).orElseThrow().getStatus());
        mvc.perform(put("/api/v1/expert/courses/" + id).with(user(MANAGER).roles("MANAGER")).contentType("application/json").content("{}"))
            .andExpect(status().isForbidden());
    }
    @Test void approvalPublishesAndRecordsReviewerAndAudit() throws Exception {
        Long id = pending("Approve");
        mvc.perform(post("/api/v1/manager/courses/" + id + "/approve").with(user(MANAGER).roles("MANAGER")))
            .andExpect(status().isOk()).andExpect(jsonPath("status").value("PUBLISHED"));
        var detail = manager.get(id);
        assertEquals(CourseStatus.PUBLISHED, detail.status()); assertNotNull(detail.reviewedBy()); assertNull(detail.rejectionReason());
        assertTrue(logs.findAll().stream().anyMatch(l -> "COURSE_APPROVED".equals(l.getAction()) && id.toString().equals(l.getTargetId()) && MANAGER.equals(l.getActor().getEmail())));
        mvc.perform(get("/api/v1/public/courses")).andExpect(status().isOk()).andExpect(jsonPath("$[0].title").value("Approve"));
        mvc.perform(post("/api/v1/manager/courses/" + id + "/reject").with(user(MANAGER).roles("MANAGER")).contentType("application/json").content("{\"reason\":\"Too late\"}"))
            .andExpect(status().isConflict());
        assertEquals(CourseStatus.PUBLISHED, courses.findById(id).orElseThrow().getStatus());
    }
    @Test void rejectionValidatesReasonAndAllowsExpertRevision() throws Exception {
        Long id = pending("Reject");
        for (String reason : new String[]{"", "   ", "a".repeat(256)})
            mvc.perform(post("/api/v1/manager/courses/" + id + "/reject").with(user(MANAGER).roles("MANAGER")).contentType("application/json").content("{\"reason\":\"" + reason + "\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/manager/courses/" + id + "/reject").with(user(MANAGER).roles("MANAGER")).contentType("application/json").content("{\"reason\":\"  Cần sửa mô tả  \"}"))
            .andExpect(status().isOk()).andExpect(jsonPath("status").value("REJECTED")).andExpect(jsonPath("rejectionReason").value("Cần sửa mô tả"));
        assertEquals("Cần sửa mô tả", expert.get(id, OWNER).rejectionReason());
        assertTrue(expert.get(id, OWNER).editable());
        assertTrue(logs.findAll().stream().anyMatch(l -> "COURSE_REJECTED".equals(l.getAction()) && "Cần sửa mô tả".equals(l.getMetadata())));
        mvc.perform(post("/api/v1/manager/courses/" + id + "/approve").with(user(MANAGER).roles("MANAGER"))).andExpect(status().isConflict());
    }
    @Test void reviewRequiresManagerAndPendingState() throws Exception {
        Long id = draft("Draft");
        mvc.perform(get("/api/v1/manager/courses")).andExpect(status().isUnauthorized());
        for (String role : new String[]{"ADMIN", "EXPERT", "STUDENT"}) {
            mvc.perform(get("/api/v1/manager/courses/" + id).with(user("other@test.local").roles(role))).andExpect(status().isForbidden());
            for (String action : new String[]{"approve", "reject"})
                mvc.perform(post("/api/v1/manager/courses/" + id + "/" + action).with(user("other@test.local").roles(role)).contentType("application/json").content("{\"reason\":\"Reason\"}"))
                    .andExpect(status().isForbidden());
        }
        mvc.perform(post("/api/v1/manager/courses/" + id + "/approve").with(user(MANAGER).roles("MANAGER"))).andExpect(status().isConflict());
        mvc.perform(post("/api/v1/manager/courses/" + Long.MAX_VALUE + "/approve").with(user(MANAGER).roles("MANAGER"))).andExpect(status().isNotFound());
        assertEquals(403, assertThrows(ApiException.class, () -> workflow.approve(id, OWNER)).status().value());
    }
}

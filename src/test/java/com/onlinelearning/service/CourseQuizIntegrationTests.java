package com.onlinelearning.service;

import com.onlinelearning.dto.course.CourseRequest;
import com.onlinelearning.dto.question.QuestionRequest;
import com.onlinelearning.dto.quiz.*;
import com.onlinelearning.entity.*;
import com.onlinelearning.exception.ApiException;
import com.onlinelearning.repository.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import java.math.BigDecimal;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties="spring.datasource.url=jdbc:h2:mem:course_quiz_tests;MODE=MySQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE")
@ActiveProfiles("test") @AutoConfigureMockMvc
class CourseQuizIntegrationTests {
    @MockitoBean com.onlinelearning.api.ChatMediaStorage storage;
    @Autowired CourseQuizService assessments;
    @Autowired ExpertQuizService quizzes;
    @Autowired ExpertCourseService courses;
    @Autowired QuestionRepository questions;
    @Autowired QuizRepository quizRepository;
    @Autowired CourseRepository courseRepository;
    @Autowired UserRepository users;
    @Autowired QuizAttemptRepository attempts;
    @Autowired CourseSubmissionReadinessService readiness;
    @Autowired PlatformTransactionManager transactions;
    @Autowired ObjectMapper json;
    @Autowired MockMvc mvc;
    static final String OWNER="expert@onlinelearning.local";
    static final String STUDENT="student@onlinelearning.local";
    Long course() { return courses.create(OWNER, new CourseRequest("Assessment", "Description", "Java", BigDecimal.ZERO)).id(); }
    QuestionRequest question(String content) { return new QuestionRequest(content,"SINGLE_CHOICE","Explanation",true,null,List.of(
        new QuestionRequest.Option("A",true),new QuestionRequest.Option("B",false),new QuestionRequest.Option("C",false),new QuestionRequest.Option("D",false))); }
    CourseQuizRequest request(Long id, List<CourseQuizRequest.Item> items) { return new CourseQuizRequest(id,"Final quiz",30,70,3,items); }
    CourseQuizRequest.Item item(Long id, String content) { return new CourseQuizRequest.Item(id,2,question(content)); }

    @Test void savesQuestionsAndOptionsWithQuizThenUpdatesWithoutCreatingSecondQuiz() {
        Long c=course(); var created=assessments.save(c,OWNER,request(null,List.of(item(null,"First"),item(null,"Second"))));
        assertTrue(created.active()); assertEquals(2,created.questions().size());
        assertEquals(4,created.questions().getFirst().question().options().size());
        var first=created.questions().getFirst(); var second=created.questions().getLast();
        var updated=assessments.save(c,OWNER,request(created.id(),List.of(item(second.questionId(),"Second edited"),item(first.questionId(),"First edited"))));
        assertEquals(created.id(),updated.id()); assertEquals(second.questionId(),updated.questions().getFirst().questionId());
        assertEquals("Second edited",updated.questions().getFirst().question().content());
        assertEquals(1,assessments.list(c,OWNER).size()); assertEquals(1,updated.questions().getFirst().position());
    }
    @Test void bothApisRejectSecondQuizIncludingInactiveExistingQuiz() {
        Long c=course(); quizzes.create(c,OWNER,new QuizRequest("Draft",null,null,null,false,null,List.of()));
        assertEquals(409,assertThrows(ApiException.class,()->assessments.save(c,OWNER,request(null,List.of(item(null,"Q"))))).status().value());
        assertEquals(409,assertThrows(ApiException.class,()->quizzes.create(c,OWNER,new QuizRequest("Second",10,70,3,true,null,List.of()))).status().value());
    }
    @Test void existingInactiveQuizCanBeCompletedInsteadOfCreatingAnother() {
        Long c=course();var draft=quizzes.create(c,OWNER,new QuizRequest("Old draft",null,null,null,false,null,List.of()));
        var saved=assessments.save(c,OWNER,request(draft.id(),List.of(item(null,"Completed"))));
        assertEquals(draft.id(),saved.id());assertNull(saved.lessonId());assertTrue(saved.active());
        assertEquals(1,assessments.list(c,OWNER).size());
    }
    @Test void concurrentCreatesCannotProduceTwoQuizzes() throws Exception {
        Long c=course();var start=new java.util.concurrent.CountDownLatch(1);
        var pool=java.util.concurrent.Executors.newFixedThreadPool(2);
        try {
            java.util.concurrent.Callable<Integer> task=()->{start.await();try {assessments.save(c,OWNER,request(null,List.of(item(null,"Concurrent"))));return 200;}catch(ApiException e){return e.status().value();}};
            var first=pool.submit(task);var second=pool.submit(task);start.countDown();
            var results=new java.util.ArrayList<>(List.of(first.get(20,java.util.concurrent.TimeUnit.SECONDS),second.get(20,java.util.concurrent.TimeUnit.SECONDS)));
            java.util.Collections.sort(results);assertEquals(List.of(200,409),results);assertEquals(1,assessments.list(c,OWNER).size());
        } finally {pool.shutdownNow();}
    }
    @Test void invalidQuestionDoesNotPartiallyPersistQuizOrQuestions() {
        Long c=course(); long before=questions.count();
        var invalid=new CourseQuizRequest.Item(null,1,new QuestionRequest("Bad","SINGLE_CHOICE","",true,null,List.of(
            new QuestionRequest.Option("A",false),new QuestionRequest.Option("B",false),new QuestionRequest.Option("C",false),new QuestionRequest.Option("D",false))));
        assertThrows(ApiException.class,()->assessments.save(c,OWNER,request(null,List.of(item(null,"Valid"),invalid))));
        assertEquals(before,questions.count()); assertTrue(assessments.list(c,OWNER).isEmpty());
    }
    @Test void failureWhileSavingAttachmentRollsBackPreviouslyCreatedQuestions() {
        Long c=course(); long before=questions.count(); var q=question("Bad attachment");
        var invalid=new CourseQuizRequest.Item(null,1,new QuestionRequest(q.content(),q.type(),q.explanation(),true,Long.MAX_VALUE,q.options()));
        assertThrows(ApiException.class,()->assessments.save(c,OWNER,request(null,List.of(item(null,"Valid first"),invalid))));
        assertEquals(before,questions.count()); assertTrue(assessments.list(c,OWNER).isEmpty());
    }
    @Test void rejectsForeignOrUnlinkedQuestionIdsAndDuplicateIds() {
        Long c=course(),other=course();var a=assessments.save(c,OWNER,request(null,List.of(item(null,"A"))));
        var b=assessments.save(other,OWNER,request(null,List.of(item(null,"B"))));
        assertThrows(ApiException.class,()->assessments.save(c,OWNER,request(a.id(),List.of(item(b.questions().getFirst().questionId(),"Attack")))));
        Long q=a.questions().getFirst().questionId(); assertThrows(ApiException.class,()->assessments.save(c,OWNER,request(a.id(),List.of(item(q,"A"),item(q,"A")))));
        assertEquals("A",assessments.list(c,OWNER).getFirst().questions().getFirst().question().content());
    }
    @Test void retainsAttemptsAndBlocksEditingPublishedCourse() {
        Long c=course();var a=assessments.save(c,OWNER,request(null,List.of(item(null,"A"))));
        new TransactionTemplate(transactions).executeWithoutResult(status->{var attempt=new QuizAttempt();attempt.setQuiz(quizRepository.findById(a.id()).orElseThrow());attempt.setStudent(users.findByEmailIgnoreCase(STUDENT).orElseThrow());attempts.saveAndFlush(attempt);});
        assertTrue(assessments.list(c,OWNER).getFirst().retained());
        assertEquals(409,assertThrows(ApiException.class,()->assessments.save(c,OWNER,request(a.id(),List.of(item(null,"Changed"))))).status().value());
        Long published=course();new TransactionTemplate(transactions).executeWithoutResult(status->courseRepository.findById(published).orElseThrow().setStatus(CourseStatus.PUBLISHED));
        assertEquals(409,assertThrows(ApiException.class,()->assessments.save(published,OWNER,request(null,List.of(item(null,"Changed"))))).status().value());
    }
    @Test void readinessRejectsMissingAndEmptyQuizAcceptsValidQuiz() {
        Long c=course();assertFalse(readiness.get(c,OWNER).checks().stream().filter(check->check.code().equals("QUIZZES")).findFirst().orElseThrow().complete());
        var empty=quizzes.create(c,OWNER,new QuizRequest("Empty",15,70,3,true,null,List.of()));
        assertFalse(readiness.get(c,OWNER).checks().stream().filter(check->check.code().equals("QUIZZES")).findFirst().orElseThrow().complete());
        assessments.save(c,OWNER,request(empty.id(),List.of(item(null,"Valid"))));
        assertTrue(readiness.get(c,OWNER).checks().stream().filter(check->check.code().equals("QUIZZES")).findFirst().orElseThrow().complete());
    }
    @Test void httpProtectsAnswersByRoleAndOwnershipAndValidatesNestedPayload() throws Exception {
        Long c=course();String path="/api/v1/expert/courses/"+c+"/assessment";
        mvc.perform(get(path)).andExpect(status().isUnauthorized());
        for(String role:List.of("STUDENT","MANAGER","ADMIN")) {
            mvc.perform(get(path).with(user(STUDENT).roles(role))).andExpect(status().isForbidden());
            mvc.perform(put(path).with(user(STUDENT).roles(role)).contentType("application/json").content("{}")).andExpect(status().isForbidden());
        }
        mvc.perform(get(path).with(user(STUDENT).roles("EXPERT"))).andExpect(status().isForbidden());
        mvc.perform(put(path).with(user(OWNER).roles("EXPERT")).contentType("application/json").content(json.writeValueAsString(request(null,List.of())))).andExpect(status().isBadRequest());
        mvc.perform(put(path).with(user(OWNER).roles("EXPERT")).contentType("application/json").content(json.writeValueAsString(request(null,List.of(item(null,"HTTP question")))))).andExpect(status().isOk()).andExpect(jsonPath("questions[0].question.options[0].correct").value(true));
    }
}

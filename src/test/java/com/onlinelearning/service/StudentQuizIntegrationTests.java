package com.onlinelearning.service;

import com.onlinelearning.dto.course.CourseRequest;
import com.onlinelearning.dto.question.QuestionRequest;
import com.onlinelearning.dto.quiz.*;
import com.onlinelearning.dto.quiz.StudentQuizDtos.*;
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
import java.time.Instant;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest @ActiveProfiles("test") @Transactional @AutoConfigureMockMvc
class StudentQuizIntegrationTests {
    @Autowired StudentQuizService studentQuiz;
    @Autowired ExpertQuizService quizzes;
    @Autowired ExpertQuestionService questions;
    @Autowired ExpertCourseService courses;
    @Autowired UserRepository users;
    @Autowired CourseRepository repository;
    @Autowired CourseRegistrationRepository registrations;
    @Autowired QuizAttemptRepository attempts;
    @Autowired EntityManager em;
    @Autowired MockMvc mvc;
    static final String OWNER="expert@onlinelearning.local", STUDENT="student@onlinelearning.local";
    record Fixture(Long courseId,Long quizId,Long first,Long second,List<Long> firstOptions,List<Long> secondOptions,CourseRegistration registration) {}
    Fixture fixture(int limit) {
        Long courseId=courses.create(OWNER,new CourseRequest("Student quiz course","Description","Java",BigDecimal.ZERO)).id();
        var options=List.of(new QuestionRequest.Option("A",true),new QuestionRequest.Option("B",false),new QuestionRequest.Option("C",false),new QuestionRequest.Option("D",false));
        var first=questions.create(courseId,OWNER,new QuestionRequest("Question one","SINGLE_CHOICE","Explanation one",true,null,options));
        var multiple=List.of(new QuestionRequest.Option("A",true),new QuestionRequest.Option("B",true),new QuestionRequest.Option("C",false),new QuestionRequest.Option("D",false));
        var second=questions.create(courseId,OWNER,new QuestionRequest("Question two","MULTIPLE_CHOICE","Explanation two",true,null,multiple));
        var quiz=quizzes.create(courseId,OWNER,new QuizRequest("Assessment",1,70,limit,true,null,List.of(new QuizQuestionItem(first.id(),1,1),new QuizQuestionItem(second.id(),2,2))));
        var course=repository.findById(courseId).orElseThrow();course.setStatus(CourseStatus.PUBLISHED);
        var registration=new CourseRegistration();registration.setCourse(course);registration.setStudent(users.findByEmailIgnoreCase(STUDENT).orElseThrow());registration.setStatus(RegistrationStatus.ACTIVE);registration.setValidFrom(Instant.now().minusSeconds(60));registration.setValidUntil(Instant.now().plusSeconds(3600));registration.setLifetimeSnapshot(false);registrations.saveAndFlush(registration);
        return new Fixture(courseId,quiz.id(),first.id(),second.id(),first.options().stream().map(o->o.id()).toList(),second.options().stream().map(o->o.id()).toList(),registration);
    }
    @Test void startResumesAndEnforcesAttemptLimit() {
        var f=fixture(1);var a=studentQuiz.start(f.quizId(),STUDENT);assertEquals(a.id(),studentQuiz.start(f.quizId(),STUDENT).id());
        studentQuiz.submit(a.id(),STUDENT,new Submission(List.of()));assertEquals(409,assertThrows(ApiException.class,()->studentQuiz.start(f.quizId(),STUDENT)).status().value());
    }
    @Test void autosaveScoresExactMultipleChoiceAndLocksSubmittedAnswers() {
        var f=fixture(3);var a=studentQuiz.start(f.quizId(),STUDENT);var answer=new Answer(f.first(),List.of(f.firstOptions().getFirst()));
        assertEquals(answer.optionIds(),studentQuiz.save(a.id(),STUDENT,answer).questions().getFirst().selectedIds());
        var result=studentQuiz.submit(a.id(),STUDENT,new Submission(List.of(answer,new Answer(f.second(),List.of(f.secondOptions().getFirst())))));
        assertEquals(33.33,result.score());assertFalse(result.passed());assertEquals(1,result.correctQuestions());
        assertEquals(result.score(),studentQuiz.submit(a.id(),STUDENT,new Submission(List.of())).score());
        assertEquals(409,assertThrows(ApiException.class,()->studentQuiz.save(a.id(),STUDENT,answer)).status().value());
        var retry=studentQuiz.start(f.quizId(),STUDENT);var perfect=studentQuiz.submit(retry.id(),STUDENT,new Submission(List.of(answer,new Answer(f.second(),f.secondOptions().subList(0,2)))));
        assertEquals(100d,perfect.score());assertTrue(perfect.passed());
    }
    @Test void deadlineIgnoresLateAnswersAndFinalizesSavedWork() {
        var f=fixture(3);var a=studentQuiz.start(f.quizId(),STUDENT);studentQuiz.save(a.id(),STUDENT,new Answer(f.first(),List.of(f.firstOptions().getFirst())));
        attempts.findById(a.id()).orElseThrow().setStartedAt(Instant.now().minusSeconds(61));
        var result=studentQuiz.submit(a.id(),STUDENT,new Submission(List.of(new Answer(f.second(),f.secondOptions().subList(0,2)))));
        assertEquals(33.33,result.score());assertEquals(60,result.secondsUsed());assertNotNull(studentQuiz.read(a.id(),STUDENT).submittedAt());
    }
    @Test void foreignOptionsQuestionsAndDuplicateSubmissionAreRejected() {
        var f=fixture(3);var a=studentQuiz.start(f.quizId(),STUDENT);
        assertEquals(400,assertThrows(ApiException.class,()->studentQuiz.save(a.id(),STUDENT,new Answer(f.first(),List.of(f.secondOptions().getFirst())))).status().value());
        assertThrows(ApiException.class,()->studentQuiz.save(a.id(),STUDENT,new Answer(Long.MAX_VALUE,List.of())));
        var answer=new Answer(f.first(),List.of());assertThrows(ApiException.class,()->studentQuiz.submit(a.id(),STUDENT,new Submission(List.of(answer,answer))));
    }
    @Test void expiryAndOwnershipAreEnforced() {
        var f=fixture(3);var a=studentQuiz.start(f.quizId(),STUDENT);
        var other=new User();other.setEmail("quiz-other@test.local");other.setFullName("Other");other.setPasswordHash("unused");other.setEnabled(true);other.setRoles(new HashSet<>(users.findByEmailIgnoreCase(STUDENT).orElseThrow().getRoles()));users.saveAndFlush(other);
        assertEquals(404,assertThrows(ApiException.class,()->studentQuiz.read(a.id(),other.getEmail())).status().value());
        assertEquals(404,assertThrows(ApiException.class,()->studentQuiz.review(a.id(),other.getEmail())).status().value());
        assertEquals(403,assertThrows(ApiException.class,()->studentQuiz.start(f.quizId(),other.getEmail())).status().value());
        f.registration().setValidUntil(Instant.now().minusSeconds(1));assertEquals(403,assertThrows(ApiException.class,()->studentQuiz.start(f.quizId(),STUDENT)).status().value());
    }
    @Test void httpDoesNotLeakCorrectAnswersBeforeSubmission() throws Exception {
        var f=fixture(3);var a=studentQuiz.start(f.quizId(),STUDENT);String path="/api/v1/student/quiz-attempts/"+a.id();
        mvc.perform(get(path).with(user(STUDENT).roles("STUDENT"))).andExpect(status().isOk())
            .andExpect(jsonPath("questions[0].correctIds").doesNotExist()).andExpect(jsonPath("questions[0].explanation").doesNotExist()).andExpect(jsonPath("questions[0].options[0].correct").doesNotExist());
        mvc.perform(get(path+"/result").with(user(STUDENT).roles("STUDENT"))).andExpect(status().isConflict());
        mvc.perform(get(path).with(user(OWNER).roles("EXPERT"))).andExpect(status().isForbidden());
    }
}

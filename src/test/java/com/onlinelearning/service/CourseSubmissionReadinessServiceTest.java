package com.onlinelearning.service;

import com.onlinelearning.entity.*;
import com.onlinelearning.exception.ApiException;
import com.onlinelearning.repository.CourseRepository;
import jakarta.persistence.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import java.math.BigDecimal;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** UC66/69: independently exercise actual readiness rules, without Spring/DB. */
@ExtendWith(MockitoExtension.class)
class CourseSubmissionReadinessServiceTest {
    @Mock CourseRepository courses;
    @Mock EntityManager em;
    @InjectMocks CourseSubmissionReadinessService service;
    private Course course;
    private Lesson lesson;
    private Quiz quiz;
    private TypedQuery<Long> packages;
    private TypedQuery<Lesson> lessons;
    private TypedQuery<Quiz> quizzes;

    @BeforeEach
    void setup() {
        course = new Course(); course.setId(1L); course.setTitle("Java"); course.setCategory("Programming");
        course.setDescription("Learn Java"); course.setPrice(BigDecimal.ZERO); course.setThumbnail(new MediaAsset());
        MediaAsset video = new MediaAsset(); video.setResourceType("video");
        lesson = new Lesson(); lesson.setTitle("Introduction"); lesson.setContent("Content");
        lesson.setPosition(1); lesson.setActive(true); lesson.setVideo(video);
        quiz = new Quiz(); quiz.setId(1L); quiz.setTitle("Quiz"); quiz.setActive(true); quiz.setDurationMinutes(10);
        quiz.setPassRate(50); quiz.setMaxAttempts(1);
        var question = new Question(); question.setContent("Q"); question.setType("SINGLE_CHOICE");
        for(int i=0;i<4;i++){var option=new QuestionOption();option.setContent("Option "+i);option.setCorrect(i==0);question.getOptions().add(option);}
        var link=new QuizQuestion();link.setQuestion(question);link.setPoints(1);
        TypedQuery<QuizQuestion> items=mock(TypedQuery.class);
        lenient().when(em.createQuery(anyString(),eq(QuizQuestion.class))).thenReturn(items);
        lenient().when(items.setParameter("id",1L)).thenReturn(items);
        lenient().when(items.getResultList()).thenReturn(List.of(link));
        packages = mock(TypedQuery.class); lessons = mock(TypedQuery.class); quizzes = mock(TypedQuery.class);
        when(em.createQuery(anyString(), eq(Long.class))).thenReturn(packages);
        when(packages.setParameter("id", 1L)).thenReturn(packages); when(packages.getSingleResult()).thenReturn(1L);
        when(em.createQuery(anyString(), eq(Lesson.class))).thenReturn(lessons);
        when(lessons.setParameter("id", 1L)).thenReturn(lessons); when(lessons.getResultList()).thenReturn(List.of(lesson));
        when(em.createQuery(anyString(), eq(Quiz.class))).thenReturn(quizzes);
        when(quizzes.setParameter("id", 1L)).thenReturn(quizzes); when(quizzes.getResultList()).thenReturn(List.of(quiz));
    }

    @ParameterizedTest
    @EnumSource(value=CourseStatus.class, names={"DRAFT","REJECTED"})
    void completeCourseIsReadyInSubmissionStates(CourseStatus status) {
        course.setStatus(status);
        var result = service.inspect(course);
        assertTrue(result.eligible()); assertTrue(result.ready());
        assertTrue(result.checks().stream().allMatch(CourseSubmissionReadinessService.Check::complete));
    }

    @ParameterizedTest
    @EnumSource(value=CourseStatus.class, names={"DRAFT","REJECTED"}, mode=EnumSource.Mode.EXCLUDE)
    void completeCourseCannotSubmitFromOtherStates(CourseStatus status) {
        course.setStatus(status); assertFalse(service.inspect(course).ready());
    }

    @ParameterizedTest
    @ValueSource(strings={"title","category","description","thumbnail","price","packages","lessons","quizzes"})
    void missingRequiredDataBlocksSubmission(String field) {
        switch (field) {
            case "title" -> course.setTitle(" ");
            case "category" -> course.setCategory(null);
            case "description" -> course.setDescription("");
            case "thumbnail" -> course.setThumbnail(null);
            case "price" -> course.setPrice(new BigDecimal("-1"));
            case "packages" -> when(packages.getSingleResult()).thenReturn(0L);
            case "lessons" -> when(lessons.getResultList()).thenReturn(List.of());
            case "quizzes" -> when(quizzes.getResultList()).thenReturn(List.of());
        }
        assertFalse(service.inspect(course).ready());
        assertThrows(ApiException.class, () -> service.requireReady(course));
    }

    @ParameterizedTest
    @ValueSource(strings={"inactive","title","content","position","video","videoType"})
    void invalidLessonBlocksSubmission(String invalid) {
        switch (invalid) {
            case "inactive" -> lesson.setActive(false);
            case "title" -> lesson.setTitle(" ");
            case "content" -> lesson.setContent(null);
            case "position" -> lesson.setPosition(0);
            case "video" -> lesson.setVideo(null);
            case "videoType" -> lesson.getVideo().setResourceType("image");
        }
        assertFalse(service.inspect(course).ready());
    }

    @ParameterizedTest
    @ValueSource(strings={"inactive","title","duration","passBelowZero","passAbove100","attempts"})
    void invalidQuizBlocksSubmission(String invalid) {
        switch (invalid) {
            case "inactive" -> quiz.setActive(false);
            case "title" -> quiz.setTitle(" ");
            case "duration" -> quiz.setDurationMinutes(0);
            case "passBelowZero" -> quiz.setPassRate(-1);
            case "passAbove100" -> quiz.setPassRate(101);
            case "attempts" -> quiz.setMaxAttempts(0);
        }
        assertFalse(service.inspect(course).ready());
    }

    @ParameterizedTest
    @ValueSource(ints={0,100})
    void passRateBoundaryIsAccepted(int passRate) {
        quiz.setPassRate(passRate); assertTrue(service.inspect(course).ready());
    }

    @Test void multipleQuizzesAndLessonScopedQuizBlockSubmission() {
        when(quizzes.getResultList()).thenReturn(List.of(quiz,quiz));
        assertFalse(service.inspect(course).ready());
        when(quizzes.getResultList()).thenReturn(List.of(quiz));quiz.setLesson(new Lesson());
        assertFalse(service.inspect(course).ready());
    }

    @Test void quizWithoutQuestionsCannotBeSubmitted() {
        when(em.createQuery("",QuizQuestion.class).getResultList()).thenReturn(List.of());
        assertFalse(service.inspect(course).ready());
    }
}

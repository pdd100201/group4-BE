package com.onlinelearning.service;

import com.onlinelearning.api.ChatMediaStorage;
import com.onlinelearning.dto.question.QuestionRequest;
import com.onlinelearning.dto.quiz.*;
import com.onlinelearning.entity.*;
import com.onlinelearning.exception.ApiException;
import com.onlinelearning.repository.*;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties="spring.datasource.url=jdbc:h2:mem:curriculum_tests;MODE=MySQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE")
@ActiveProfiles("test") @Transactional
class CourseCurriculumIntegrationTests {
 @MockitoBean ChatMediaStorage storage;
 @Autowired CourseCurriculumService curriculum;
 @Autowired ExpertQuestionService questions;
 @Autowired ExpertQuizService quizzes;
 @Autowired ExpertLessonService lessonService;
 @Autowired CourseRepository courses;
 @Autowired UserRepository users;
 @Autowired EntityManager em;
 static final String OWNER="expert@onlinelearning.local";
 record Fixture(Course course,Lesson first,Lesson second) {}
 Fixture fixture(){
  var c=new Course();c.setTitle("Curriculum "+UUID.randomUUID().toString().substring(0,8));c.setExpert(users.findByEmailIgnoreCase(OWNER).orElseThrow());c.setStatus(CourseStatus.DRAFT);courses.saveAndFlush(c);
  var a=new Lesson();a.setCourse(c);a.setTitle("First");a.setContent("First content");a.setPosition(5);em.persist(a);
  var b=new Lesson();b.setCourse(c);b.setTitle("Second");b.setContent("Second content");b.setPosition(10);em.persist(b);em.flush();return new Fixture(c,a,b);
 }
 QuestionRequest question(Long lesson){return new QuestionRequest("Question","SINGLE_CHOICE","Explanation",true,null,List.of(new QuestionRequest.Option("A",true),new QuestionRequest.Option("B",false),new QuestionRequest.Option("C",false),new QuestionRequest.Option("D",false)),lesson);}
 @Test void reordersAtomicallyAndNormalizesPositions(){
  var f=fixture();var before=List.of(f.first().getId(),f.second().getId());var after=List.of(f.second().getId(),f.first().getId());
  var result=curriculum.reorder(f.course().getId(),OWNER,after,before);assertEquals(after,result.lessons().stream().map(CourseCurriculumService.LessonItem::id).toList());assertEquals(List.of(1,2),result.lessons().stream().map(CourseCurriculumService.LessonItem::position).toList());
  assertEquals(1L,em.createQuery("select count(a) from AuditLog a where a.action='CURRICULUM_REORDERED' and a.targetId=:id",Long.class).setParameter("id",f.course().getId().toString()).getSingleResult());
 }
 @Test void rejectsStaleMissingDuplicateAndForeignIds(){
  var f=fixture();var ids=List.of(f.first().getId(),f.second().getId());
  assertEquals(409,assertThrows(ApiException.class,()->curriculum.reorder(f.course().getId(),OWNER,ids,List.of())).status().value());
  assertEquals(400,assertThrows(ApiException.class,()->curriculum.reorder(f.course().getId(),OWNER,List.of(f.first().getId(),f.first().getId()),ids)).status().value());
  assertThrows(ApiException.class,()->curriculum.reorder(f.course().getId(),OWNER,List.of(f.first().getId()),ids));
  var other=fixture();assertThrows(ApiException.class,()->curriculum.reorder(f.course().getId(),OWNER,List.of(f.first().getId(),other.first().getId()),ids));
  assertEquals(ids,curriculum.read(f.course().getId(),OWNER).lessons().stream().map(CourseCurriculumService.LessonItem::id).toList());
 }
 @Test void protectsOwnershipAndPublishedContent(){
  var f=fixture();assertEquals(403,assertThrows(ApiException.class,()->curriculum.read(f.course().getId(),"student@onlinelearning.local")).status().value());
  f.course().setStatus(CourseStatus.PUBLISHED);var ids=List.of(f.first().getId(),f.second().getId());assertEquals(409,assertThrows(ApiException.class,()->curriculum.reorder(f.course().getId(),OWNER,ids,ids)).status().value());
 }
 @Test void organizesBankQuestionsAndQuizzesWithoutDuplicatingQuestions(){
  var f=fixture();var scoped=questions.create(f.course().getId(),OWNER,question(f.first().getId()));questions.create(f.course().getId(),OWNER,question(null));
  Quiz legacy=new Quiz();legacy.setCourse(f.course());legacy.setLesson(f.first());legacy.setTitle("Legacy lesson quiz");em.persist(legacy);
  QuizQuestion link=new QuizQuestion();link.setQuiz(legacy);link.setQuestion(em.find(Question.class,scoped.id()));link.setPosition(1);link.setPoints(1);em.persist(link);
  Quiz oldFinal=new Quiz();oldFinal.setCourse(f.course());oldFinal.setTitle("Legacy final quiz");em.persist(oldFinal);em.flush();
  var outline=curriculum.read(f.course().getId(),OWNER);assertEquals(1,outline.sharedQuestions());assertEquals(1,outline.lessons().getFirst().bankQuestions());assertEquals(1,outline.lessons().getFirst().quizzes().size());assertEquals(1,outline.lessons().getFirst().quizzes().getFirst().questionCount());assertEquals(1,outline.courseQuizzes().size());
  assertEquals(1,questions.list(f.course().getId(),OWNER,"","",null,0,10,f.first().getId()).getTotalElements());
  assertEquals(1,questions.list(f.course().getId(),OWNER,"","",null,0,10,0L).getTotalElements());
  assertEquals(1,quizzes.questionBank(f.course().getId(),OWNER,"",0,20,f.first().getId()).getTotalElements());
  assertEquals(0,quizzes.questionBank(f.course().getId(),OWNER,"",0,20,f.second().getId()).getTotalElements());
 }
 @Test void rejectsQuestionScopedToAnotherCourse(){
  var f=fixture();var other=fixture();assertEquals(400,assertThrows(ApiException.class,()->questions.create(f.course().getId(),OWNER,question(other.first().getId()))).status().value());
 }
 @Test void keepsOldRequestsAsSharedAndAllowsReclassification(){
  var f=fixture();var old=new QuestionRequest("Question","SINGLE_CHOICE","",true,null,question(null).options());var q=questions.create(f.course().getId(),OWNER,old);assertNull(q.lessonId());
  assertEquals(f.first().getId(),questions.update(f.course().getId(),q.id(),OWNER,question(f.first().getId())).lessonId());
 }
 @Test void preventsDeletingLessonWithScopedQuestions(){
  var f=fixture();var q=questions.create(f.course().getId(),OWNER,question(f.first().getId()));assertEquals(409,assertThrows(ApiException.class,()->lessonService.delete(f.course().getId(),f.first().getId(),OWNER)).status().value());
  questions.update(f.course().getId(),q.id(),OWNER,question(null));lessonService.delete(f.course().getId(),f.first().getId(),OWNER);assertEquals(1,curriculum.read(f.course().getId(),OWNER).lessons().size());
 }
 @Test void preventsDeletingLessonWithLinkedQuiz(){
  var f=fixture();Quiz legacy=new Quiz();legacy.setCourse(f.course());legacy.setTitle("Legacy quiz");legacy.setLesson(f.first());em.persist(legacy);em.flush();assertEquals(409,assertThrows(ApiException.class,()->lessonService.delete(f.course().getId(),f.first().getId(),OWNER)).status().value());
 }
}

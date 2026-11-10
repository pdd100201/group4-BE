package com.onlinelearning.service;

import com.onlinelearning.api.ChatMediaStorage;
import com.onlinelearning.dto.quiz.StudentQuizDtos.*;
import com.onlinelearning.entity.*;
import com.onlinelearning.entity.Question;
import com.onlinelearning.repository.*;
import com.onlinelearning.exception.ApiException;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.*;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.test.web.servlet.MockMvc;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties="spring.datasource.url=jdbc:h2:mem:certificate_tests;MODE=MySQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE")
@ActiveProfiles("test") @Transactional @AutoConfigureMockMvc
class CertificateIntegrationTests {
 @MockitoBean ChatMediaStorage storage;
 @Autowired CertificateService service;
 @Autowired StudentLessonService learning;
 @Autowired StudentQuizService quizzes;
 @Autowired UserRepository users;
 @Autowired CourseRepository courses;
 @Autowired CourseRegistrationRepository registrations;
 @Autowired CourseCertificateRepository certificates;
 @Autowired EntityManager em;
 @Autowired MockMvc mvc;
 @Autowired PlatformTransactionManager transactionManager;
 static final String STUDENT="student@onlinelearning.local",EXPERT="expert@onlinelearning.local",ADMIN="admin@onlinelearning.local",MANAGER="manager@onlinelearning.local";
 record Fixture(Course course,CourseRegistration registration,Lesson lesson,Quiz quiz,Question question,QuestionOption correct) {}
 User actor(String email){return users.findByEmailIgnoreCase(email).orElseThrow();}
 Fixture fixture(){
  var c=new Course();c.setTitle("Khóa Java "+UUID.randomUUID().toString().substring(0,8));c.setExpert(actor(EXPERT));c.setStatus(CourseStatus.PUBLISHED);courses.saveAndFlush(c);
  var l=new Lesson();l.setCourse(c);l.setTitle("Bài học đầu tiên");l.setContent("Nội dung");l.setPosition(1);em.persist(l);
  var question=new Question();question.setCourse(c);question.setContent("Chọn đáp án đúng");question.setType("SINGLE_CHOICE");
  var yes=new QuestionOption();yes.setQuestion(question);yes.setContent("Đúng");yes.setCorrect(true);question.getOptions().add(yes);
  var no=new QuestionOption();no.setQuestion(question);no.setContent("Sai");no.setCorrect(false);question.getOptions().add(no);em.persist(question);
  var q=new Quiz();q.setCourse(c);q.setTitle("Kiểm tra cuối khóa");q.setDurationMinutes(10);q.setPassRate(70);q.setMaxAttempts(3);em.persist(q);
  var link=new QuizQuestion();link.setQuiz(q);link.setQuestion(question);link.setPosition(1);link.setPoints(1);em.persist(link);q.getQuizQuestions().add(link);
  var r=new CourseRegistration();r.setCourse(c);r.setStudent(actor(STUDENT));r.setStatus(RegistrationStatus.ACTIVE);r.setValidFrom(Instant.now().minusSeconds(60));r.setValidUntil(Instant.now().plusSeconds(3600));r.setLifetimeSnapshot(false);registrations.saveAndFlush(r);em.flush();
  return new Fixture(c,r,l,q,question,yes);
 }
 void policy(Fixture f){service.savePolicy(MANAGER,f.course().getId(),true,"EduLearn",List.of(f.quiz().getId()),0);}
 void progress(Fixture f){
  var p=new StudentLessonProgress();p.setStudent(actor(STUDENT));p.setLesson(f.lesson());p.setRegistration(f.registration());p.setCompleted(true);p.setCompletedAt(Instant.now());p.setProgressPercent(100);em.persist(p);em.flush();
 }
 QuizAttempt passing(Fixture f){
  var a=new QuizAttempt();a.setStudent(actor(STUDENT));a.setQuiz(f.quiz());a.setScore(100d);a.setPassed(true);a.setPassRateSnapshot(70);a.setSubmittedAt(Instant.now());em.persist(a);em.flush();return a;
 }
 CertificateService.View issue(Fixture f){policy(f);progress(f);passing(f);var result=service.checkAndIssue(STUDENT,f.course().getId());assertNotNull(result.certificate());return result.certificate();}
 long certCount(Long course){return em.createQuery("select count(c) from CourseCertificate c where c.course.id=:id",Long.class).setParameter("id",course).getSingleResult();}

 @Test void issuesOnceWithEvidenceAuditAndNotification(){
  var f=fixture();var c=issue(f);assertEquals("ISSUED",c.status());assertTrue(c.code().matches("[0-9A-HJKMNP-TV-Z]{8}"));
  assertEquals(c.id(),service.checkAndIssue(STUDENT,f.course().getId()).certificate().id());assertEquals(1,certCount(f.course().getId()));
  assertEquals(1,service.history(ADMIN,c.id(),0).getTotalElements());
  var row=certificates.findById(c.id()).orElseThrow();assertTrue(row.getEvidenceSnapshot().contains("quizAttempts"));
  assertEquals(1L,em.createQuery("select count(n) from UserNotification n where n.eventKey=:key",Long.class).setParameter("key","certificate-issued:"+c.id()).getSingleResult());
 }
 @Test void missingLessonOrPassedQuizPreventsIssuance(){
  var f=fixture();policy(f);
  var missing=service.checkAndIssue(STUDENT,f.course().getId());assertFalse(missing.eligible());assertFalse(missing.lessons().getFirst().complete());assertFalse(missing.quizzes().getFirst().complete());
  progress(f);assertNull(service.checkAndIssue(STUDENT,f.course().getId()).certificate());
  var attempt=passing(f);attempt.setPassed(false);em.flush();assertNull(service.checkAndIssue(STUDENT,f.course().getId()).certificate());
  attempt.setPassed(true);attempt.setSubmittedAt(null);em.flush();assertNull(service.checkAndIssue(STUDENT,f.course().getId()).certificate());
 }
 @Test void activeAndPublishedAccessIsRequiredForFirstIssue(){
  var f=fixture();policy(f);progress(f);passing(f);f.registration().setValidUntil(Instant.now().minusSeconds(1));em.flush();
  assertFalse(service.checkAndIssue(STUDENT,f.course().getId()).eligible());assertEquals(0,certCount(f.course().getId()));
  f.registration().setValidUntil(Instant.now().plusSeconds(3600));f.course().setStatus(CourseStatus.ARCHIVED);em.flush();
  assertFalse(service.checkAndIssue(STUDENT,f.course().getId()).eligible());
 }
 @Test void disabledPolicyCannotIssueAndHistoryIsVersioned(){
  var f=fixture();progress(f);passing(f);
  assertFalse(service.checkAndIssue(STUDENT,f.course().getId()).enabled());
  policy(f);service.savePolicy(ADMIN,f.course().getId(),false,"EduLearn",List.of(f.quiz().getId()),1);
  assertFalse(service.checkAndIssue(STUDENT,f.course().getId()).enabled());assertEquals(2,service.policy(MANAGER,f.course().getId()).history().size());
 }
 @Test void contentChangesRequireNewPolicyWithoutSilentlyRelaxingRequirements(){
  var f=fixture();policy(f);progress(f);passing(f);f.question().setContent("Câu hỏi đã thay đổi");em.flush();
  var result=service.checkAndIssue(STUDENT,f.course().getId());assertFalse(result.eligible());assertTrue(result.reasons().stream().anyMatch(s->s.contains("thay đổi")));
  service.savePolicy(MANAGER,f.course().getId(),true,"EduLearn",List.of(f.quiz().getId()),1);
  assertNotNull(service.checkAndIssue(STUDENT,f.course().getId()).certificate());
 }
 @Test void rejectsInvalidOrForeignQuizAndStalePolicyVersion(){
  var f=fixture();var other=fixture();
  assertThrows(ApiException.class,()->service.savePolicy(MANAGER,f.course().getId(),true,"EduLearn",List.of(other.quiz().getId()),0));
  assertThrows(ApiException.class,()->service.savePolicy(MANAGER,f.course().getId(),true,"EduLearn",List.of(),0));
  policy(f);assertEquals(409,assertThrows(ApiException.class,()->service.savePolicy(ADMIN,f.course().getId(),true,"EduLearn",List.of(f.quiz().getId()),0)).status().value());
 }
 @Test void rejectsIncompleteQuizAndEmptyCoursePolicy(){
  var f=fixture();f.quiz().setActive(false);em.flush();
  assertThrows(ApiException.class,()->service.savePolicy(MANAGER,f.course().getId(),true,"EduLearn",List.of(f.quiz().getId()),0));
  f.quiz().setActive(true);f.lesson().setActive(false);em.flush();
  assertThrows(ApiException.class,()->service.savePolicy(MANAGER,f.course().getId(),true,"EduLearn",List.of(f.quiz().getId()),0));
 }
 @Test void historicalSnapshotsSurviveProfileCourseAndAccessChanges(){
  var f=fixture();var c=issue(f);String name=c.studentName(),title=c.courseTitle();
  actor(STUDENT).setFullName("Tên mới");f.course().setTitle("Tên khóa mới");f.registration().setValidUntil(Instant.now().minusSeconds(1));f.course().setStatus(CourseStatus.ARCHIVED);em.flush();
  var read=service.mineDetail(STUDENT,c.id());assertEquals(name,read.studentName());assertEquals(title,read.courseTitle());assertEquals(title,service.verify(c.code()).courseTitle());
 }
 @Test void ownerPrivacyAndRoleChecksAreEnforced() throws Exception {
  var f=fixture();var c=issue(f);
  var other=new User();other.setEmail("other-"+UUID.randomUUID()+"@test.local");other.setFullName("Other");other.setPasswordHash("unused");other.setEnabled(true);other.setRoles(new HashSet<>(actor(STUDENT).getRoles()));users.saveAndFlush(other);
  assertEquals(404,assertThrows(ApiException.class,()->service.mineDetail(other.getEmail(),c.id())).status().value());
  assertThrows(ApiException.class,()->service.savePolicy(STUDENT,f.course().getId(),false,"EduLearn",List.of(),1));
  assertThrows(ApiException.class,()->service.checkAndIssue(other.getEmail(),f.course().getId()));
  mvc.perform(get("/api/v1/student/certificates/"+c.id()+"/pdf").with(user(other.getEmail()).roles("STUDENT"))).andExpect(status().isNotFound());
  mvc.perform(get("/api/v1/admin/certificates").with(user(STUDENT).roles("STUDENT"))).andExpect(status().isForbidden());
 }
 @Test void revokedCertificateCannotBeReissuedAndHasOneRevocationAudit(){
  var f=fixture();var c=issue(f);
  assertThrows(ApiException.class,()->service.revoke(STUDENT,c.id(),"No"));
  assertThrows(ApiException.class,()->service.revoke(ADMIN,c.id()," "));
  service.revoke(MANAGER,c.id(),"Sai thông tin");service.revoke(ADMIN,c.id(),"Repeated");em.flush();
  assertEquals("REVOKED",service.verify(c.code()).status());assertEquals(2,service.history(ADMIN,c.id(),0).getTotalElements());
  assertEquals(c.id(),service.checkAndIssue(STUDENT,f.course().getId()).certificate().id());assertEquals(1,certCount(f.course().getId()));
 }
 @Test void publicApiExposesOnlyVerificationFieldsAndSupportsRevocation() throws Exception {
  var f=fixture();var c=issue(f);
  mvc.perform(get("/api/v1/public/certificates/"+c.code())).andExpect(status().isOk()).andExpect(jsonPath("$.studentName").value(c.studentName())).andExpect(jsonPath("$.studentId").doesNotExist()).andExpect(jsonPath("$.revocationReason").doesNotExist()).andExpect(jsonPath("$.registrationId").doesNotExist()).andExpect(jsonPath("$.email").doesNotExist()).andExpect(header().string("Cache-Control","no-store"));
  service.revoke(ADMIN,c.id(),"Private administrative reason");
  mvc.perform(get("/api/v1/public/certificates/"+c.code())).andExpect(jsonPath("$.status").value("REVOKED")).andExpect(jsonPath("$.revocationReason").doesNotExist());
  mvc.perform(get("/api/v1/public/certificates/not-a-code")).andExpect(status().isNotFound());
 }
 @Test void lessonCompletionAutomaticallyIssuesWhenQuizAlreadyPassed(){
  var f=fixture();policy(f);passing(f);learning.complete(f.course().getId(),f.lesson().getId(),STUDENT);
  assertEquals(1,certCount(f.course().getId()));learning.complete(f.course().getId(),f.lesson().getId(),STUDENT);assertEquals(1,certCount(f.course().getId()));
 }
 @Test void realQuizSubmissionAutomaticallyIssuesWhenLessonsCompleted(){
  var f=fixture();policy(f);progress(f);var attempt=quizzes.start(f.quiz().getId(),STUDENT);
  var result=quizzes.submit(attempt.id(),STUDENT,new Submission(List.of(new Answer(f.question().getId(),List.of(f.correct().getId())))));
  assertTrue(result.passed());assertEquals(1,certCount(f.course().getId()));
 }
 @Test void revokedPdfIsDeniedButIssuedPdfWorksAfterAccessExpires() throws Exception {
  var f=fixture();var c=issue(f);f.registration().setValidUntil(Instant.now().minusSeconds(1));em.flush();
  mvc.perform(get("/api/v1/student/certificates/"+c.id()+"/pdf").with(user(STUDENT).roles("STUDENT"))).andExpect(status().isOk()).andExpect(content().contentType("application/pdf")).andExpect(header().string("Cache-Control","no-store"));
  service.revoke(ADMIN,c.id(),"Reason");
  mvc.perform(get("/api/v1/student/certificates/"+c.id()+"/pdf").with(user(STUDENT).roles("STUDENT"))).andExpect(status().isConflict());
 }
 @Test void blockedUsersCannotReadOrIssue(){
  var f=fixture();var c=issue(f);actor(STUDENT).setBlocked(true);em.flush();
  assertThrows(ApiException.class,()->service.mineDetail(STUDENT,c.id()));assertThrows(ApiException.class,()->service.checkAndIssue(STUDENT,f.course().getId()));
 }
 @Test void listsOnlyCoursesReadyToConfigureWhenRequested(){
  var f=fixture();
  String title=f.course().getTitle();
  assertEquals(List.of(f.course().getId()),service.courseOptions(MANAGER,title,0,true).map(CertificateService.CourseOption::id).getContent());

  f.course().setStatus(CourseStatus.DRAFT);em.flush();
  assertTrue(service.courseOptions(MANAGER,title,0,true).isEmpty());
  assertEquals(1,service.courseOptions(MANAGER,title,0,false).getTotalElements());

  f.course().setStatus(CourseStatus.PUBLISHED);f.lesson().setActive(false);em.flush();
  assertTrue(service.courseOptions(MANAGER,title,0,true).isEmpty());

  f.lesson().setActive(true);f.quiz().setActive(false);em.flush();
  assertTrue(service.courseOptions(MANAGER,title,0,true).isEmpty());
 }
 @Test void listsFilterByStatusCourseAndEscapedSearch(){
  var f=fixture();var c=issue(f);
  assertEquals(1,service.management(ADMIN,"","ISSUED",f.course().getId(),0).getTotalElements());
  assertEquals(0,service.management(ADMIN,"%","all",f.course().getId(),0).getTotalElements());
  assertEquals(c.id(),service.mine(STUDENT,c.code(),"all",0).getContent().getFirst().id());
  assertThrows(ApiException.class,()->service.mine(STUDENT,"","invalid",0));
  assertThrows(ApiException.class,()->service.history(ADMIN,c.id(),-1));
 }
 @Test @Transactional(propagation=Propagation.NOT_SUPPORTED) void concurrentIssueRequestsReturnSameCertificate() throws Exception {
  var template=new TransactionTemplate(transactionManager);
  Long course=template.execute(status->{var f=fixture();policy(f);progress(f);passing(f);return f.course().getId();});
  ExecutorService workers=Executors.newFixedThreadPool(2);
  try{
   var first=workers.submit(()->service.checkAndIssue(STUDENT,course).certificate().id());
   var second=workers.submit(()->service.checkAndIssue(STUDENT,course).certificate().id());
   assertEquals(first.get(20,TimeUnit.SECONDS),second.get(20,TimeUnit.SECONDS));
   assertEquals(1L,template.execute(status->certCount(course)).longValue());
  }finally{workers.shutdownNow();}
 }
}

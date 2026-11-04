package com.onlinelearning.service;

import com.onlinelearning.entity.*;
import com.onlinelearning.repository.*;
import com.onlinelearning.exception.ApiException;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.test.web.servlet.MockMvc;
import java.time.Instant;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest @ActiveProfiles("test") @Transactional @AutoConfigureMockMvc
class LessonDiscussionIntegrationTests {
 @org.springframework.test.context.bean.override.mockito.MockitoBean com.onlinelearning.api.ChatMediaStorage chatStorage;
 @Autowired LessonDiscussionService service;
 @Autowired UserRepository users;
 @Autowired CourseRegistrationRepository registrations;
 @Autowired EntityManager em;
 @Autowired MockMvc mvc;
 static final String STUDENT="student@onlinelearning.local";
 static final String EXPERT="expert@onlinelearning.local";
 static final String MANAGER="manager@onlinelearning.local";
 record Fixture(Lesson lesson,CourseRegistration registration) {}
 Fixture fixture(){
  var course=new Course();course.setTitle("Discussion course");course.setExpert(users.findByEmailIgnoreCase(EXPERT).orElseThrow());course.setStatus(CourseStatus.PUBLISHED);em.persist(course);
  var lesson=new Lesson();lesson.setCourse(course);lesson.setTitle("Lesson one");lesson.setPosition(1);em.persist(lesson);
  var r=new CourseRegistration();r.setCourse(course);r.setStudent(users.findByEmailIgnoreCase(STUDENT).orElseThrow());r.setStatus(RegistrationStatus.ACTIVE);
  r.setValidFrom(Instant.now().minusSeconds(60));r.setValidUntil(Instant.now().plusSeconds(3600));r.setLifetimeSnapshot(false);registrations.saveAndFlush(r);
  return new Fixture(lesson,r);
 }
 User other(String email,String role){
  var u=new User();u.setEmail(email);u.setFullName("Other user");u.setPasswordHash("unused");u.setEnabled(true);
  u.setRoles(new java.util.HashSet<>(users.findByEmailIgnoreCase(role.equals("EXPERT")?EXPERT:STUDENT).orElseThrow().getRoles()));
  return users.saveAndFlush(u);
 }
 @Test void questionReplyResolutionAndReopen(){
  var f=fixture();var q=service.create(STUDENT,f.lesson().getId()," Need help ","Question body");
  assertEquals("Need help",q.title());
  var a=service.reply(EXPERT,q.id(),"Explanation");
  assertTrue(a.expert());service.resolve(STUDENT,q.id(),a.id());em.flush();
  assertEquals(a.id(),service.detail(STUDENT,q.id(),0).question().acceptedAnswerId());
  assertEquals(1,service.list(EXPERT,null,"Need","resolved",0).getTotalElements());
  service.resolve(EXPERT,q.id(),null);em.flush();
  assertNull(service.detail(STUDENT,q.id(),0).question().acceptedAnswerId());
 }
 @Test void expiryAndUnenrolledStudentsCannotAccessEvenOwnQuestions(){
  var f=fixture();var q=service.create(STUDENT,f.lesson().getId(),"Question","Body");
  var outsider=other("outsider@discussion.test","STUDENT");
  assertEquals(403,assertThrows(ApiException.class,()->service.detail(outsider.getEmail(),q.id(),0)).status().value());
  f.registration().setValidUntil(Instant.now().minusSeconds(1));em.flush();
  assertEquals(403,assertThrows(ApiException.class,()->service.detail(STUDENT,q.id(),0)).status().value());
  assertThrows(ApiException.class,()->service.reply(STUDENT,q.id(),"Reply"));
 }
 @Test void expertScopeAndStudentManagementAreRestricted(){
  var f=fixture();var q=service.create(STUDENT,f.lesson().getId(),"Question","Body");
  var outsider=other("other-expert@discussion.test","EXPERT");
  assertEquals(0,service.list(outsider.getEmail(),null,"","all",0).getTotalElements());
  assertThrows(ApiException.class,()->service.reply(outsider.getEmail(),q.id(),"Reply"));
  assertThrows(ApiException.class,()->service.hideQuestion(STUDENT,q.id(),true));
  assertThrows(ApiException.class,()->service.list(STUDENT,null,"","all",0));
  assertThrows(ApiException.class,()->service.create(EXPERT,f.lesson().getId(),"Question","Body"));
 }
 @Test void studentsCannotReplyToTheirOwnOrPeersQuestions(){
  var f=fixture();var q=service.create(STUDENT,f.lesson().getId(),"Question","Body");
  var peer=other("peer@discussion.test","STUDENT");
  var r=new CourseRegistration();r.setStudent(peer);r.setCourse(f.lesson().getCourse());r.setStatus(RegistrationStatus.ACTIVE);r.setValidFrom(Instant.now().minusSeconds(1));r.setLifetimeSnapshot(true);registrations.saveAndFlush(r);
  assertFalse(service.detail(STUDENT,q.id(),0).question().canReply());
  assertEquals(403,assertThrows(ApiException.class,()->service.reply(STUDENT,q.id(),"Self reply")).status().value());
  assertEquals(403,assertThrows(ApiException.class,()->service.reply(peer.getEmail(),q.id(),"Peer reply")).status().value());
  var a=service.reply(EXPERT,q.id(),"Expert reply");
  assertTrue(a.expert());assertThrows(ApiException.class,()->service.resolve(peer.getEmail(),q.id(),a.id()));
 }
 @Test void moderationHidesContentAndUnsetsAcceptedAnswer(){
  var f=fixture();var q=service.create(STUDENT,f.lesson().getId(),"Question","Body");
  var a=service.reply(EXPERT,q.id(),"Answer");service.resolve(STUDENT,q.id(),a.id());em.flush();
  service.hideAnswer(MANAGER,q.id(),a.id(),true);em.flush();
  assertNull(service.detail(STUDENT,q.id(),0).question().acceptedAnswerId());
  assertEquals(0,service.detail(STUDENT,q.id(),0).answers().getTotalElements());
  assertEquals(1,service.detail(MANAGER,q.id(),0).answers().getTotalElements());
  service.hideQuestion(MANAGER,q.id(),true);em.flush();
  assertEquals(0,service.list(EXPERT,null,"","all",0).getTotalElements());
  assertThrows(ApiException.class,()->service.detail(STUDENT,q.id(),0));
  assertFalse(service.detail(MANAGER,q.id(),0).question().canReply());
  service.hideQuestion(MANAGER,q.id(),false);em.flush();
  assertEquals(q.id(),service.detail(STUDENT,q.id(),0).question().id());
 }
 @Test void rejectsForeignAnswerAndInvalidInput(){
  var f=fixture();var q=service.create(STUDENT,f.lesson().getId(),"Question","Body");
  var second=service.create(STUDENT,f.lesson().getId(),"Other","Body");
  var a=service.reply(EXPERT,second.id(),"Answer");
  assertThrows(ApiException.class,()->service.resolve(STUDENT,q.id(),a.id()));
  assertThrows(ApiException.class,()->service.create(STUDENT,f.lesson().getId()," ","Body"));
  assertThrows(ApiException.class,()->service.reply(EXPERT,q.id(),"x".repeat(4001)));
  assertThrows(ApiException.class,()->service.list(STUDENT,f.lesson().getId(),"","all",-1));
  f.lesson().setActive(false);em.flush();
  assertThrows(ApiException.class,()->service.detail(STUDENT,q.id(),0));
 }
 @Test void searchEscapesWildcardsAndFiltersUnanswered(){
  var f=fixture();var a=service.create(STUDENT,f.lesson().getId(),"100% Java","Body");
  service.create(STUDENT,f.lesson().getId(),"Other","Body");
  assertEquals(1,service.list(STUDENT,f.lesson().getId(),"%","all",0).getTotalElements());
  service.reply(EXPERT,a.id(),"Answer");
  assertEquals(1,service.list(EXPERT,null,"","unanswered",0).getTotalElements());
 }
 @Test void apiRequiresAuthenticationAndValidatesBodies() throws Exception {
  var f=fixture();
  mvc.perform(get("/api/v1/discussions").param("lessonId",f.lesson().getId().toString())).andExpect(status().isUnauthorized());
  mvc.perform(post("/api/v1/discussions").with(user(STUDENT).roles("STUDENT")).contentType("application/json")
   .content("{\"lessonId\":"+f.lesson().getId()+",\"title\":\"\",\"content\":\"Body\"}")).andExpect(status().isBadRequest());
  mvc.perform(get("/api/v1/discussions").with(user(STUDENT).roles("STUDENT"))).andExpect(status().isForbidden());
  var q=service.create(STUDENT,f.lesson().getId(),"Question","Body");
  mvc.perform(post("/api/v1/discussions/"+q.id()+"/answers").with(user(STUDENT).roles("STUDENT")).contentType("application/json").content("{\"content\":\"Self reply\"}")).andExpect(status().isForbidden());
 }
 @Test void paginationAndBlockedAccountsAreEnforced(){
  var f=fixture();
  for(int i=0;i<21;i++)service.create(STUDENT,f.lesson().getId(),"Question "+i,"Body");
  var first=service.list(STUDENT,f.lesson().getId(),"","all",0);
  var next=service.list(STUDENT,f.lesson().getId(),"","all",1);
  assertEquals(20,first.getContent().size());assertEquals(1,next.getContent().size());
  assertTrue(first.getContent().stream().noneMatch(q->q.id().equals(next.getContent().getFirst().id())));
  f.registration().getStudent().setBlocked(true);em.flush();
  assertThrows(ApiException.class,()->service.detail(STUDENT,first.getContent().getFirst().id(),0));
 }
 @Test void unpublishedCourseRemainsVisibleOnlyToStaff(){
  var f=fixture();var q=service.create(STUDENT,f.lesson().getId(),"Question","Body");
  f.lesson().getCourse().setStatus(CourseStatus.ARCHIVED);em.flush();
  assertEquals(404,assertThrows(ApiException.class,()->service.detail(STUDENT,q.id(),0)).status().value());
  assertEquals(q.id(),service.detail(EXPERT,q.id(),0).question().id());
  assertEquals(q.id(),service.detail(MANAGER,q.id(),0).question().id());
 }
}

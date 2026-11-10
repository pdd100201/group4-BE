package com.onlinelearning.service;

import com.onlinelearning.api.ChatMediaStorage;
import com.onlinelearning.dto.course.*;
import com.onlinelearning.dto.registration.RegistrationRequest;
import com.onlinelearning.dto.payment.*;
import com.onlinelearning.entity.*;
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
import java.math.BigDecimal;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties={
 "spring.datasource.url=jdbc:h2:mem:notification_test;MODE=MySQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
 "SEPAY_MODE=test","SEPAY_BANK=TESTBANK","SEPAY_ACCOUNT_NUMBER=100100","SEPAY_QR_ACCOUNT=100100",
 "SEPAY_ACCOUNT_HOLDER=Test Receiver","SEPAY_WEBHOOK_API_KEY=unit-test-only-key-with-at-least-32-characters","SEPAY_DESCRIPTION_PREFIX=SWP"})
@ActiveProfiles("test") @Transactional @AutoConfigureMockMvc
class NotificationIntegrationTests {
 @MockitoBean ChatMediaStorage storage;
 @Autowired NotificationService notifications;
 @Autowired NotificationEvents events;
 @Autowired UserNotificationRepository notices;
 @Autowired UserRepository users;
 @Autowired CourseRepository courses;
 @Autowired CourseRegistrationRepository registrations;
 @Autowired PaymentRepository paymentRows;
 @Autowired TransactionRepository transactions;
 @Autowired StudentRegistrationService registrationService;
 @Autowired PaymentService payments;
 @Autowired CoursePackageService packages;
 @Autowired CourseAnnouncementService announcements;
 @Autowired LessonDiscussionService discussions;
 @Autowired CourseModerationService moderation;
 @Autowired CourseRestorationService restoration;
 @Autowired CourseService workflow;
 @Autowired EntityManager em;
 @Autowired MockMvc mvc;
 @Autowired PlatformTransactionManager transactionManager;
 static final String STUDENT="student@onlinelearning.local",EXPERT="expert@onlinelearning.local",ADMIN="admin@onlinelearning.local",MANAGER="manager@onlinelearning.local";
 record Fixture(Course course,CourseRegistration registration,Lesson lesson) {}
 User actor(String email){return users.findByEmailIgnoreCase(email).orElseThrow();}
 Fixture fixture(){
  var course=new Course();course.setTitle("Notification course");course.setDescription("Description");course.setCategory("Java");course.setPrice(BigDecimal.ZERO);course.setExpert(actor(EXPERT));course.setStatus(CourseStatus.DRAFT);courses.saveAndFlush(course);
  var offers=packages.update(course.getId(),EXPERT,new CoursePackagesRequest(Arrays.stream(PackageDuration.values()).map(d->new CoursePackagesRequest.PackageInput(d,new BigDecimal("100000"),BigDecimal.ZERO,true)).toList()));
  course.setStatus(CourseStatus.PUBLISHED);em.flush();
  var registered=registrationService.register(STUDENT,new RegistrationRequest(course.getId(),offers.getFirst().id(),"Student","0912345678"));
  var r=registrations.findById(registered.id()).orElseThrow();
  var lesson=new Lesson();lesson.setCourse(course);lesson.setTitle("First lesson");lesson.setPosition(1);em.persist(lesson);em.flush();
  return new Fixture(course,r,lesson);
 }
 void activate(Fixture f){var r=f.registration();r.setStatus(RegistrationStatus.ACTIVE);r.setValidFrom(Instant.now().minusSeconds(60));r.setValidUntil(Instant.now().plusSeconds(3600));em.flush();}
 long count(String email,String type){
  return em.createQuery("select count(n) from UserNotification n where n.recipient.id=:id and n.type=:type",Long.class).setParameter("id",actor(email).getId()).setParameter("type",type).getSingleResult();
 }
 SepayWebhook event(PaymentResponse qr,long id,String amount){
  String code=qr.transferContent().substring(qr.transferContent().lastIndexOf(' ')+1);
  String time=Instant.now().atZone(ZoneId.of("Asia/Ho_Chi_Minh")).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
  return new SepayWebhook(id,"TESTBANK",time,"100100",null,code,qr.transferContent(),"in",new BigDecimal(amount));
 }
 @Test void registrationNotifiesStudentAndExpertWithoutClaimingPayment(){
  var f=fixture();
  assertEquals(1,count(STUDENT,"REGISTRATION_CREATED"));assertEquals(1,count(EXPERT,"STUDENT_REGISTERED"));
  assertEquals(0,count(ADMIN,"TEST_PAYMENT_RECEIVED"));assertEquals(0,count(STUDENT,"PAYMENT_SUCCEEDED"));
  assertTrue(notifications.list(STUDENT,false,"COURSE",0,20).getContent().getFirst().message().contains("chờ thanh toán"));
  registrationService.register(STUDENT,new RegistrationRequest(f.course().getId(),f.registration().getCoursePackage().getId(),"Student","0912345678"));
  assertEquals(1,count(EXPERT,"STUDENT_REGISTERED"));
 }
 @Test void confirmedPaymentNotifiesEachRoleOnceAndLabelsTestTransactions(){
  var f=fixture();var qr=payments.initiate(STUDENT,f.registration().getId());
  assertEquals(0,count(STUDENT,"PAYMENT_SUCCEEDED"));
  var callback=event(qr,71001,"100000");payments.confirm(callback);payments.confirm(callback);payments.confirm(event(qr,71002,"100000"));
  assertEquals(1,count(STUDENT,"PAYMENT_SUCCEEDED"));assertEquals(1,count(EXPERT,"STUDENT_ACTIVATED"));assertEquals(1,count(ADMIN,"TEST_PAYMENT_RECEIVED"));
  var revenue=notifications.list(ADMIN,false,"PAYMENT",0,50).getContent().stream().filter(n->n.type().equals("TEST_PAYMENT_RECEIVED")).findFirst().orElseThrow();
  assertTrue(revenue.title().contains("[TEST]"));assertTrue(revenue.message().contains("không phải doanh thu thực tế"));
  assertTrue(revenue.link().startsWith("/admin/transactions/"));
 }
 @Test void expiredQrNotifiesOnceWithoutRevenue(){
  var f=fixture();payments.initiate(STUDENT,f.registration().getId());
  var tx=transactions.findAll().getFirst();tx.setCreatedAt(Instant.now().minusSeconds(400));em.flush();
  payments.expire(f.registration().getId());payments.expire(f.registration().getId());
  assertEquals(1,count(STUDENT,"PAYMENT_EXPIRED"));assertEquals(0,count(STUDENT,"PAYMENT_SUCCEEDED"));
 }
 @Test void mismatchedAmountProducesAdminReviewButNoSuccess(){
  var f=fixture();var qr=payments.initiate(STUDENT,f.registration().getId());
  assertThrows(ApiException.class,()->payments.confirm(event(qr,72001,"90000")));
  assertEquals(0,count(STUDENT,"PAYMENT_SUCCEEDED"));assertEquals(0,count(ADMIN,"TEST_PAYMENT_RECEIVED"));
  assertTrue(count(ADMIN,"PAYMENT_REVIEW_REQUIRED")>=1);
  assertEquals(RegistrationStatus.PENDING_PAYMENT,f.registration().getStatus());
 }
 @Test void ownInboxIsPrivateAndReadOperationsAreIdempotent() throws Exception {
  fixture();var studentNotice=notifications.list(STUDENT,false,"all",0,20).getContent().getFirst();
  assertEquals(404,assertThrows(ApiException.class,()->notifications.read(EXPERT,studentNotice.id())).status().value());
  mvc.perform(put("/api/v1/notifications/"+studentNotice.id()+"/read").with(user(EXPERT).roles("EXPERT"))).andExpect(status().isNotFound());
  notifications.read(STUDENT,studentNotice.id());notifications.read(STUDENT,studentNotice.id());
  assertEquals(0,notifications.summary(STUDENT).unreadCount());
  assertEquals(1,notifications.summary(EXPERT).unreadCount());
  mvc.perform(get("/api/v1/notifications")).andExpect(status().isUnauthorized());
 }
 @Test void readAllPreservesNewerUnseenNotifications(){
  fixture();Long boundary=notifications.summary(STUDENT).latestId();
  notifications.send(actor(STUDENT).getId(),"later-test","COURSE_ANNOUNCEMENT","COURSE","Later","Message","/student/courses");
  notifications.readAll(STUDENT,boundary);
  assertEquals(1,notifications.summary(STUDENT).unreadCount());
  assertEquals("Later",notifications.list(STUDENT,true,"all",0,20).getContent().getFirst().title());
 }
 @Test void duplicateEventsDoNotResetReadStateAndBlockedRecipientsAreSkipped(){
  Long id=actor(STUDENT).getId();
  notifications.send(id,"duplicate-test","COURSE_ANNOUNCEMENT","COURSE","First","Message","/student/courses");
  var item=notifications.list(STUDENT,false,"all",0,20).getContent().getFirst();notifications.read(STUDENT,item.id());
  notifications.send(id,"duplicate-test","COURSE_ANNOUNCEMENT","COURSE","Retry","Message","/student/courses");
  assertEquals(1,notifications.list(STUDENT,false,"all",0,20).getTotalElements());assertEquals(0,notifications.summary(STUDENT).unreadCount());
  actor(STUDENT).setBlocked(true);em.flush();
  notifications.send(id,"blocked-test","COURSE_ANNOUNCEMENT","COURSE","Blocked","Message","/student/courses");
  assertEquals(1,em.createQuery("select count(n) from UserNotification n where n.recipient.id=:id",Long.class).setParameter("id",id).getSingleResult());
  assertThrows(ApiException.class,()->notifications.summary(STUDENT));
 }
 @Test void announcementsReachOnlyCurrentEnrolleesAndRetryDoesNotResend(){
  var f=fixture();activate(f);
  var expired=new User();expired.setEmail("expired@notification.test");expired.setFullName("Expired");expired.setPasswordHash("unused");expired.setEnabled(true);expired.setRoles(new HashSet<>(actor(STUDENT).getRoles()));users.saveAndFlush(expired);
  var r=new CourseRegistration();r.setStudent(expired);r.setCourse(f.course());r.setStatus(RegistrationStatus.ACTIVE);r.setValidFrom(Instant.now().minusSeconds(120));r.setValidUntil(Instant.now().minusSeconds(60));r.setLifetimeSnapshot(false);registrations.saveAndFlush(r);
  String key=UUID.randomUUID().toString();
  var first=announcements.create(EXPERT,f.course().getId(),key,"New schedule","Meet on Monday");
  var again=announcements.create(EXPERT,f.course().getId(),key,"New schedule","Meet on Monday");
  assertEquals(first.id(),again.id());assertEquals(1,first.recipientCount());
  assertEquals(1,count(STUDENT,"COURSE_ANNOUNCEMENT"));assertEquals(0,count(expired.getEmail(),"COURSE_ANNOUNCEMENT"));
  assertThrows(ApiException.class,()->announcements.create(STUDENT,f.course().getId(),UUID.randomUUID().toString(),"No","No"));
  assertThrows(ApiException.class,()->announcements.create(EXPERT,f.course().getId(),key,"Changed","Meet on Monday"));
 }
 @Test void questionAndExpertAnswerNotifyOnlyParticipants(){
  var f=fixture();activate(f);var q=discussions.create(STUDENT,f.lesson().getId(),"Help","Question");
  assertEquals(1,count(EXPERT,"LESSON_QUESTION"));assertEquals(0,count(ADMIN,"LESSON_QUESTION"));
  discussions.reply(EXPERT,q.id(),"Explanation");assertEquals(1,count(STUDENT,"LESSON_ANSWER"));
  assertTrue(notifications.list(STUDENT,false,"DISCUSSION",0,20).getContent().getFirst().link().contains("?tab=discussions"));
 }
 @Test void archiveAndRestoreNotifyEnrolledStudentsAndExpert(){
  var f=fixture();activate(f);moderation.archive(f.course().getId(),MANAGER,"Maintenance");
  assertEquals(1,count(STUDENT,"COURSE_ARCHIVED"));assertEquals(1,count(EXPERT,"COURSE_ARCHIVED"));
  restoration.restore(f.course().getId(),ADMIN,"Ready");
  assertEquals(1,count(STUDENT,"COURSE_RESTORED"));assertEquals(1,count(EXPERT,"COURSE_RESTORED"));
 }
 @Test void archivedCourseNoticeOpensRefundChatForPendingRegistration() throws Exception {
  var f=fixture();
  Long courseId=f.course().getId(), expertId=actor(EXPERT).getId();
  moderation.archive(courseId,MANAGER,"Maintenance");
  var notice=notifications.list(STUDENT,false,"COURSE",0,20).getContent().stream()
   .filter(n->n.type().equals("COURSE_ARCHIVED")).findFirst().orElseThrow();
  assertEquals("/chat?courseId="+courseId+"&peerId="+expertId,notice.link());
  assertEquals(1,count(EXPERT,"COURSE_ARCHIVED"));
  mvc.perform(get("/api/v1/chat/contact").with(user(STUDENT).roles("STUDENT"))
   .param("courseId",courseId.toString()).param("peerId",expertId.toString()))
   .andExpect(status().isOk());
  mvc.perform(get("/api/v1/chat/contact").with(user(MANAGER).roles("MANAGER"))
   .param("courseId",courseId.toString()).param("peerId",expertId.toString()))
   .andExpect(status().isForbidden());
 }
 @Test void deletingCourseWithoutRegistrationsNotifiesItsExpert() {
  var f=fixture();
  registrations.delete(f.registration());em.flush();
  moderation.delete(f.course().getId(),ADMIN,"Cleanup");
  assertEquals(1,count(EXPERT,"COURSE_DELETED"));
  assertEquals(0,count(STUDENT,"COURSE_DELETED"));
 }
 @Test void reviewDecisionNotifiesExpert(){
  var f=fixture();f.course().setStatus(CourseStatus.PENDING_REVIEW);em.flush();workflow.reject(f.course().getId(),MANAGER,"Needs revision");
  assertEquals(1,count(EXPERT,"COURSE_REJECTED"));
 }
 @Test @Transactional(propagation=Propagation.NOT_SUPPORTED) void rolledBackBusinessDoesNotLeaveNotifications(){
  Long recipient=actor(STUDENT).getId();String key="rollback-"+UUID.randomUUID();
  new TransactionTemplate(transactionManager).execute(status->{
   notifications.send(recipient,key,"COURSE_ANNOUNCEMENT","COURSE","Rollback","Message","/student/courses");status.setRollbackOnly();return null;
  });
  assertEquals(0,notices.findAll((r,q,cb)->cb.equal(r.get("eventKey"),key)).size());
 }
 @Test void paginationAndCategoryValidation(){
  for(int i=0;i<23;i++)notifications.send(actor(STUDENT).getId(),"page-"+i,"COURSE_ANNOUNCEMENT","COURSE","Title","Message","/student/courses");
  assertEquals(20,notifications.list(STUDENT,false,"COURSE",0,20).getContent().size());
  assertEquals(3,notifications.list(STUDENT,false,"COURSE",1,20).getContent().size());
  assertThrows(ApiException.class,()->notifications.list(STUDENT,false,"invalid",0,20));
  assertThrows(ApiException.class,()->notifications.list(STUDENT,false,"all",-1,20));
 }
 @Test void latePaymentAlertsAdminWithoutActivatingRegistration(){
  var f=fixture();var qr=payments.initiate(STUDENT,f.registration().getId());
  var tx=transactions.findAll().getFirst();tx.setCreatedAt(Instant.now().minusSeconds(400));em.flush();
  assertThrows(com.onlinelearning.exception.PaymentExpiredException.class,()->payments.confirm(event(qr,73001,"100000")));
  assertEquals(RegistrationStatus.CANCELLED,f.registration().getStatus());assertEquals(0,count(STUDENT,"PAYMENT_SUCCEEDED"));
  assertTrue(notifications.list(ADMIN,false,"PAYMENT",0,50).getContent().stream().anyMatch(n->n.type().equals("PAYMENT_REVIEW_REQUIRED")&&n.message().contains("QR hết hạn")));
 }
 @Test void announcementsEnforceExpertOwnershipAndApiValidation() throws Exception {
  var f=fixture();
  var other=new User();other.setEmail("other-expert@notification.test");other.setFullName("Other expert");other.setPasswordHash("unused");other.setEnabled(true);other.setRoles(new HashSet<>(actor(EXPERT).getRoles()));users.saveAndFlush(other);
  assertEquals(403,assertThrows(ApiException.class,()->announcements.create(other.getEmail(),f.course().getId(),UUID.randomUUID().toString(),"Title","Body")).status().value());
  String path="/api/v1/expert/courses/"+f.course().getId()+"/announcements";
  mvc.perform(post(path).with(user(STUDENT).roles("STUDENT")).contentType("application/json").content("{}")).andExpect(status().isForbidden());
  mvc.perform(post(path).with(user(EXPERT).roles("EXPERT")).contentType("application/json").content("{\"requestKey\":\"invalid\",\"title\":\"Title\",\"message\":\"Body\"}")).andExpect(status().isBadRequest());
  f.course().setStatus(CourseStatus.ARCHIVED);em.flush();
  assertThrows(ApiException.class,()->announcements.create(EXPERT,f.course().getId(),UUID.randomUUID().toString(),"Title","Body"));
 }
 @Test void announcementHistoryPreventsPhysicalCourseDeletion(){
  var f=fixture();announcements.create(EXPERT,f.course().getId(),UUID.randomUUID().toString(),"Title","Body");
  // Remove the unpaid registration to isolate the announcement retention rule.
  registrations.delete(f.registration());em.flush();
  f.course().setStatus(CourseStatus.ARCHIVED);em.flush();
  assertEquals(409,assertThrows(ApiException.class,()->moderation.delete(f.course().getId(),ADMIN,"Cleanup")).status().value());
 }
}

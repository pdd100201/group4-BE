package com.onlinelearning.service;

import com.onlinelearning.entity.*;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@Service @RequiredArgsConstructor @Transactional(propagation=Propagation.MANDATORY)
public class NotificationEvents {
 private final NotificationService notifications;
 private final EntityManager em;

 public void registrationCreated(CourseRegistration r){
  String course=r.getCourse().getTitle(),key="registration:"+r.getId();
  send(r.getStudent(),"registered:"+key,"REGISTRATION_CREATED","COURSE","Đã tiếp nhận đăng ký",
   "Bạn đã đăng ký "+course+". Đăng ký đang chờ thanh toán; quyền học chưa được kích hoạt.","/student/registrations/"+r.getId());
  send(r.getCourse().getExpert(),"expert:"+key,"STUDENT_REGISTERED","COURSE","Có học viên đăng ký khóa học",
   r.getStudent().getFullName()+" đã đăng ký "+course+". Trạng thái: chờ thanh toán.","/expert/courses/"+r.getCourse().getId());
 }
 public void registrationCancelled(CourseRegistration r){
  send(r.getStudent(),"cancelled:registration:"+r.getId(),"REGISTRATION_CANCELLED","COURSE","Đã hủy đăng ký",
   "Bạn đã hủy đăng ký "+r.getCourse().getTitle()+(r.getValidFrom()!=null?". Quyền học của đăng ký này đã dừng.":". Không chuyển tiền theo mã QR của đăng ký này."),"/student/registrations/"+r.getId());
  if(r.getValidFrom()!=null) send(r.getCourse().getExpert(),"expert-cancelled:registration:"+r.getId(),"STUDENT_CANCELLED","COURSE","Học viên đã hủy đăng ký",
   r.getStudent().getFullName()+" đã hủy đăng ký "+r.getCourse().getTitle()+".","/expert/students/"+r.getCourse().getId());
 }
 public void paymentSucceeded(CourseRegistration r,Payment payment,Transaction tx){
  String course=r.getCourse().getTitle(), key="paid:"+payment.getId();
  boolean test="SEPAY_TEST".equals(payment.getProvider());
  String label=test?"[TEST] ":"";
  send(r.getStudent(),key,"PAYMENT_SUCCEEDED","PAYMENT",label+"Thanh toán thành công",
   "Đã xác nhận "+money(payment.getAmount())+" cho "+course+". Quyền học đã được kích hoạt.","/student/registrations/"+r.getId());
  send(r.getCourse().getExpert(),key,"STUDENT_ACTIVATED","COURSE",label+"Học viên đã kích hoạt khóa học",
   r.getStudent().getFullName()+" đã thanh toán và có quyền học "+course+".","/expert/courses/"+r.getCourse().getId());
  for(User admin:role("ADMIN"))send(admin,key,test?"TEST_PAYMENT_RECEIVED":"PAYMENT_RECEIVED","PAYMENT",
   label+"Đã ghi nhận khoản thu học phí",r.getStudent().getFullName()+" · "+course+" · "+money(payment.getAmount())+
   (test?". Giao dịch thử nghiệm, không phải doanh thu thực tế.":" · SePay đã xác nhận."),"/admin/transactions/"+tx.getId());
 }
 public void paymentExpired(CourseRegistration r,Payment p){
  send(r.getStudent(),"expired:"+p.getId(),"PAYMENT_EXPIRED","PAYMENT","Yêu cầu thanh toán đã hết hạn",
   "QR thanh toán cho "+r.getCourse().getTitle()+" đã hết hạn. Đăng ký chưa được kích hoạt; hãy kiểm tra trước khi đăng ký lại.","/student/registrations/"+r.getId());
 }
 // Validated bank callbacks requiring manual review must survive a business rollback.
 @Transactional(propagation=Propagation.REQUIRES_NEW)
 public void reconciliation(Long transactionId,String provider,long eventId,String course,String reason,BigDecimal amount,boolean test){
  for(User admin:role("ADMIN"))send(admin,"reconcile:"+provider+":"+eventId,"PAYMENT_REVIEW_REQUIRED","PAYMENT",
   (test?"[TEST] ":"")+"Giao dịch cần đối soát",course+" · "+money(amount)+". "+reason,"/admin/transactions/"+transactionId);
 }
 public void courseChange(Course c,String action,String eventKey,String reason){
  String title=c.getTitle(), expertLink="/expert/courses/"+c.getId();
  switch(action){
   case "COURSE_SUBMITTED","COURSE_RESUBMITTED" -> {
    for(User manager:role("MANAGER"))send(manager,eventKey,"COURSE_REVIEW_REQUESTED","REVIEW","Khóa học cần duyệt",
     c.getExpert().getFullName()+" đã gửi duyệt "+title+".","/manager/courses/"+c.getId()+"/review");
   }
   case "COURSE_APPROVED" -> send(c.getExpert(),eventKey,"COURSE_APPROVED","REVIEW","Khóa học đã được duyệt",title+" đã được xuất bản.",expertLink);
   case "COURSE_REJECTED" -> send(c.getExpert(),eventKey,"COURSE_REJECTED","REVIEW","Khóa học bị từ chối",title+". Lý do: "+reason,expertLink);
   case "COURSE_ARCHIVED" -> {
    String headline="Khóa học tạm ngừng hiển thị";
    List<User> students=em.createQuery("""
     select distinct r.student from CourseRegistration r where r.course.id=:courseId
     and r.student.enabled=true and r.student.blocked=false and r.student.id<>:expertId
     """,User.class).setParameter("courseId",c.getId()).setParameter("expertId",c.getExpert().getId()).getResultList();
    send(c.getExpert(),eventKey,action,"COURSE",headline,title+". Lý do: "+reason+
     (students.isEmpty()?"":" Mở Trò chuyện để trao đổi với học viên về chính sách hoàn tiền."),
     students.isEmpty()?expertLink:"/chat");
    for(User student:students)send(student,eventKey,action,"COURSE",headline,
     title+". Lý do: "+reason+" Mở Trò chuyện với giảng viên để trao đổi về chính sách hoàn tiền.",
     "/chat?courseId="+c.getId()+"&peerId="+c.getExpert().getId());
   }
   case "COURSE_DELETED" -> send(c.getExpert(),eventKey,action,"COURSE","Khóa học đã bị xóa",
    title+". Lý do: "+reason,"/expert/courses");
   case "COURSE_RESTORED" -> {
    String headline="Khóa học đã được khôi phục";
    send(c.getExpert(),eventKey,action,"COURSE",headline,title+". Lý do: "+reason,expertLink);
    for(User student:enrolled(c.getId()))send(student,eventKey,action,"COURSE",headline,
     title+". Bạn có thể tiếp tục học nếu đăng ký vẫn còn hiệu lực.","/student/courses/"+c.getId()+"/learn");
   }
   default -> { }
  }
 }
 public void questionCreated(LessonDiscussion q){
  send(q.getLesson().getCourse().getExpert(),"question:"+q.getId(),"LESSON_QUESTION","DISCUSSION","Có câu hỏi mới trong bài học",
   q.getAuthor().getFullName()+" hỏi: "+q.getTitle()+" · "+q.getLesson().getTitle(),"/expert/discussions");
 }
 public void questionAnswered(LessonDiscussionAnswer a){
  var q=a.getDiscussion();var lesson=q.getLesson();
  send(q.getAuthor(),"answer:"+a.getId(),"LESSON_ANSWER","DISCUSSION","Giảng viên đã giải đáp câu hỏi",
   q.getTitle()+" · "+lesson.getTitle(),"/student/courses/"+lesson.getCourse().getId()+"/lessons/"+lesson.getId()+"?tab=discussions");
 }
 public void postSubmitted(Post post){
  for(User manager:role("MANAGER"))send(manager,"post-submitted:"+post.getId(),"POST_REVIEW_REQUESTED","REVIEW","Có bài viết cần duyệt",
   post.getAuthor().getFullName()+" đã gửi: "+post.getTitle(),"/manager/posts/"+post.getId());
 }
 public void postReviewed(Post post){
  boolean approved=post.getStatus()==PostStatus.PUBLISHED;
  send(post.getAuthor(),"post-reviewed:"+post.getId()+":"+post.getStatus(),"POST_REVIEWED","REVIEW",
   approved?"Bài viết đã được duyệt":"Bài viết bị từ chối",post.getTitle(),"/blog/my-submissions");
 }
 public List<User> enrolled(Long courseId){
  return em.createQuery("""
   select distinct r.student from CourseRegistration r where r.course.id=:courseId
   and r.status=com.onlinelearning.entity.RegistrationStatus.ACTIVE and r.validFrom<=:now
   and (r.lifetimeSnapshot=true or r.validUntil>:now)
   and r.student.enabled=true and r.student.blocked=false
   """,User.class).setParameter("courseId",courseId).setParameter("now",Instant.now()).getResultList();
 }
 private List<User> role(String role){return em.createQuery("select distinct u from User u join u.roles r where r.code=:role and u.enabled=true and u.blocked=false",User.class).setParameter("role",role).getResultList();}
 private void send(User recipient,String key,String type,String category,String title,String message,String link){
  notifications.send(recipient.getId(),key,type,category,title,message,link);
 }
 private String money(BigDecimal value){return String.format(java.util.Locale.forLanguageTag("vi-VN"),"%,.0f đ",value);}
}
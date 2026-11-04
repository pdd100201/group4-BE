package com.onlinelearning.service;
import com.onlinelearning.entity.*;
import com.onlinelearning.repository.*;
import com.onlinelearning.exception.ApiException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.*;
import org.springframework.http.HttpStatus;
import java.time.Instant;

@Service @RequiredArgsConstructor @Transactional(readOnly=true)
public class CourseAnnouncementService {
 private final CourseRepository courses;
 private final UserRepository users;
 private final CourseAnnouncementRepository announcements;
 private final NotificationEvents events;
 private final NotificationService notifications;
 public record View(Long id,String title,String message,int recipientCount,Instant createdAt) {}
 public Page<View> history(String email,Long id,int page){
  owned(email,id,false);
  if(page<0||page>100000)throw new ApiException(HttpStatus.BAD_REQUEST,"Trang không hợp lệ.");
  return announcements.findByCourseIdOrderByIdDesc(id,PageRequest.of(page,10)).map(this::view);
 }
 @Transactional public View create(String email,Long id,String key,String title,String message){
  Course course=owned(email,id,true);
  var prior=announcements.findByCourseIdAndRequestKey(id,key);
  if(prior.isPresent()){
   if(!prior.get().getTitle().equals(title.strip())||!prior.get().getMessage().equals(message.strip()))
    throw new ApiException(HttpStatus.CONFLICT,"Mã gửi đã được sử dụng cho nội dung khác.");
   return view(prior.get());
  }
  if(course.getStatus()!=CourseStatus.PUBLISHED)throw new ApiException(HttpStatus.CONFLICT,"Chỉ gửi thông báo cho khóa học đã xuất bản.");
  var recipients=events.enrolled(id);
  var a=new CourseAnnouncement();a.setCourse(course);a.setRequestKey(key);a.setTitle(title.strip());a.setMessage(message.strip());a.setRecipientCount(recipients.size());
  announcements.saveAndFlush(a);
  for(var student:recipients)notifications.send(student.getId(),"announcement:"+a.getId(),"COURSE_ANNOUNCEMENT","COURSE",a.getTitle(),
   course.getTitle()+" · "+a.getMessage(),"/student/courses/"+id+"/learn");
  return view(a);
 }
 private Course owned(String email,Long id,boolean lock){
  User actor=users.findByEmailIgnoreCase(email).filter(u->u.isEnabled()&&!u.isBlocked()&&u.getRoles().stream().anyMatch(r->r.getCode().equals("EXPERT")))
   .orElseThrow(()->new ApiException(HttpStatus.FORBIDDEN,"Chỉ Expert phụ trách được gửi thông báo."));
  var course=(lock?courses.findForUpdate(id):courses.findById(id)).orElseThrow(()->new ApiException(HttpStatus.NOT_FOUND,"Không tìm thấy khóa học."));
  if(!course.getExpert().getId().equals(actor.getId()))throw new ApiException(HttpStatus.FORBIDDEN,"Bạn chỉ được quản lý thông báo khóa học của mình.");
  return course;
 }
 private View view(CourseAnnouncement a){return new View(a.getId(),a.getTitle(),a.getMessage(),a.getRecipientCount(),a.getCreatedAt());}
}
package com.onlinelearning.service;

import com.onlinelearning.entity.*;
import com.onlinelearning.exception.ApiException;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.util.*;

@Service @RequiredArgsConstructor @Transactional(readOnly=true)
public class ExpertStudentService {
 private final EntityManager em;
 private final ExpertCourseService courses;
 private final com.onlinelearning.repository.CourseRegistrationRepository registrations;
 public record Student(Long id, String fullName, String avatarUrl, Instant registeredAt, String registrationStatus,
     boolean currentAccess, long completedLessons, long totalLessons, String learningStatus, boolean canChat) {}
 public record Summary(long registrations, long students, long currentAccess, long notStarted, long learning, long completed) {}
 public record StudentPage(List<Student> content, long totalElements, int totalPages, int number, Summary summary) {}
 public record LessonResult(Long id, String title, boolean completed, Instant completedAt) {}
 public record QuizResult(Long id, String title, Instant startedAt, Instant submittedAt, Double score, boolean passed) {}
 public record Detail(Student student, List<LessonResult> lessons, List<QuizResult> attempts) {}
 private static final String BASE = " from CourseRegistration r where r.course.id=:course and r.id=(select max(x.id) from CourseRegistration x where x.course=r.course and x.student=r.student)";
 private static final String ACCESS = "exists (select a.id from CourseRegistration a where a.course=r.course and a.student=r.student and a.status=com.onlinelearning.entity.RegistrationStatus.ACTIVE and a.validFrom<=:now and (a.lifetimeSnapshot=true or a.validUntil>:now))";
 private static final String DONE = "(select count(p.id) from StudentLessonProgress p where p.student=r.student and p.lesson.course=r.course and p.lesson.active=true and p.completed=true)";
 private long count(String query, Long course) { return em.createQuery(query, Long.class).setParameter("course",course).getSingleResult(); }
 public StudentPage list(Long course, String email, String search, String access, String learning, int page, int size) {
  courses.get(course,email);
  if(page<0 || size<1 || size>50 || search.length()>100 || !Set.of("", "CURRENT", "INACTIVE").contains(access) || !Set.of("", "NOT_STARTED", "LEARNING", "COMPLETED").contains(learning))
   throw new ApiException(HttpStatus.BAD_REQUEST,"Bộ lọc hoặc phân trang không hợp lệ.");
  long totalLessons=count("select count(l) from Lesson l where l.course.id=:course and l.active=true",course);
  String complete= totalLessons>0 ? DONE+"="+totalLessons : "1=0";
  String started="exists (select p.id from StudentLessonProgress p where p.student=r.student and p.lesson.course=r.course and p.lesson.active=true and (p.completed=true or p.progressPercent>0)) or exists (select q.id from QuizAttempt q where q.student=r.student and q.quiz.course=r.course)";
  String learningExpr="("+started+") and not ("+complete+")";
  Instant now=Instant.now();
  long students=count("select count(r)"+BASE,course);
  long completed=count("select count(r)"+BASE+" and ("+complete+")",course);
  long inLearning=count("select count(r)"+BASE+" and ("+learningExpr+")",course);
  long current=em.createQuery("select count(r)"+BASE+" and "+ACCESS,Long.class).setParameter("course",course).setParameter("now",now).getSingleResult();
  var summary=new Summary(count("select count(r) from CourseRegistration r where r.course.id=:course",course),students,current,students-completed-inLearning,inLearning,completed);
  String where=BASE+" and locate(:search,lower(r.student.fullName))>0";
  if(!access.isEmpty()) where+=" and "+(access.equals("CURRENT") ? "" : "not ")+ACCESS;
  if(!learning.isEmpty()) where+=" and ("+switch(learning) { case "COMPLETED" -> complete; case "LEARNING" -> learningExpr; default -> "not ("+started+") and not ("+complete+")"; }+")";
  var countQuery=em.createQuery("select count(r)"+where,Long.class).setParameter("course",course).setParameter("search",search.trim().toLowerCase(Locale.ROOT));
  var query=em.createQuery("select r"+where+" order by r.createdAt desc,r.id desc",CourseRegistration.class).setParameter("course",course).setParameter("search",search.trim().toLowerCase(Locale.ROOT));
  if(!access.isEmpty()) {countQuery.setParameter("now",now);query.setParameter("now",now);}
  long total=countQuery.getSingleResult();
  var rows=query.setFirstResult((int)Math.min((long)page*size,Integer.MAX_VALUE)).setMaxResults(size).getResultList();
  return new StudentPage(rows.stream().map(r->student(r,totalLessons,now)).toList(),total,(int)Math.ceil((double)total/size),page,summary);
 }
 private Student student(CourseRegistration r,long total,Instant now) {
  var user=r.getStudent(); Long course=r.getCourse().getId();
  long done=em.createQuery("select count(p) from StudentLessonProgress p where p.student.id=:student and p.lesson.course.id=:course and p.lesson.active=true and p.completed=true",Long.class).setParameter("student",user.getId()).setParameter("course",course).getSingleResult();
  long started=em.createQuery("select count(p) from StudentLessonProgress p where p.student.id=:student and p.lesson.course.id=:course and p.lesson.active=true and (p.completed=true or p.progressPercent>0)",Long.class).setParameter("student",user.getId()).setParameter("course",course).getSingleResult();
  long attempts=em.createQuery("select count(q) from QuizAttempt q where q.student.id=:student and q.quiz.course.id=:course",Long.class).setParameter("student",user.getId()).setParameter("course",course).getSingleResult();
  boolean current=registrations.countCurrentAccess(user.getId(),course,now)>0;
  String status=r.getStatus().name();
  if(r.getStatus()==RegistrationStatus.ACTIVE && !Boolean.TRUE.equals(r.getLifetimeSnapshot()) && r.getValidUntil()!=null && !r.getValidUntil().isAfter(now)) status="EXPIRED";
  return new Student(user.getId(),user.getFullName(),user.getAvatarUrl(),r.getCreatedAt(),status,current,done,total,total>0 && done==total ? "COMPLETED" : started>0 || attempts>0 ? "LEARNING" : "NOT_STARTED",current && user.isEnabled() && !user.isBlocked() && !user.getId().equals(r.getCourse().getExpert().getId()));
 }
 public Detail detail(Long course,Long student,String email) {
  courses.get(course,email);
  var rows=em.createQuery("select r from CourseRegistration r where r.course.id=:course and r.student.id=:student order by r.id desc",CourseRegistration.class).setParameter("course",course).setParameter("student",student).setMaxResults(1).getResultList();
  if(rows.isEmpty()) throw new ApiException(HttpStatus.NOT_FOUND,"Học viên chưa đăng ký khóa học này.");
  var lessons=em.createQuery("select l from Lesson l where l.course.id=:course and l.active=true order by l.position,l.id",Lesson.class).setParameter("course",course).getResultList();
  var progress=em.createQuery("select p from StudentLessonProgress p where p.student.id=:student and p.lesson.course.id=:course",StudentLessonProgress.class).setParameter("course",course).setParameter("student",student).getResultList();
  var byLesson=new HashMap<Long,StudentLessonProgress>();progress.forEach(p->byLesson.put(p.getLesson().getId(),p));
  var results=lessons.stream().map(l->{var p=byLesson.get(l.getId());return new LessonResult(l.getId(),l.getTitle(),p!=null && p.isCompleted(),p==null?null:p.getCompletedAt());}).toList();
  var attempts=em.createQuery("select a from QuizAttempt a join fetch a.quiz where a.student.id=:student and a.quiz.course.id=:course order by a.startedAt desc,a.id desc",QuizAttempt.class).setParameter("course",course).setParameter("student",student).getResultList().stream().map(a->new QuizResult(a.getId(),a.getQuiz().getTitle(),a.getStartedAt(),a.getSubmittedAt(),a.getScore(),a.isPassed())).toList();
  return new Detail(student(rows.getFirst(),lessons.size(),Instant.now()),results,attempts);
 }
}

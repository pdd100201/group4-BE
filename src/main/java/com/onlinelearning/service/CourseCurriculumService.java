package com.onlinelearning.service;

import com.onlinelearning.entity.*;
import com.onlinelearning.exception.ApiException;
import com.onlinelearning.repository.*;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.util.*;

@Service @RequiredArgsConstructor @Transactional(readOnly=true)
public class CourseCurriculumService {
 private final CourseRepository courses;
 private final LessonRepository lessons;
 private final EntityManager em;
 public record QuizItem(Long id,String title,boolean active,int questionCount,Integer durationMinutes,Integer passRate,Integer maxAttempts) {}
 public record LessonItem(Long id,String title,Integer position,boolean active,long bankQuestions,List<QuizItem> quizzes) {}
 public record Outline(List<LessonItem> lessons,List<QuizItem> courseQuizzes,long sharedQuestions) {}
 public Outline read(Long courseId,String email) {
  owned(courseId,email,false);
  var rows=lessons.findByCourseIdOrderByPositionAscIdAsc(courseId);
  var quizRows=em.createQuery("select q from Quiz q where q.course.id=:id order by q.id",Quiz.class).setParameter("id",courseId).getResultList();
  Map<Long,Long> counts=new HashMap<>();
  for(Object[] row:em.createQuery("select q.lesson.id,count(q) from Question q where q.course.id=:id group by q.lesson.id",Object[].class).setParameter("id",courseId).getResultList()) counts.put((Long)row[0],(Long)row[1]);
  Map<Long,List<QuizItem>> grouped=new HashMap<>();
  Map<Long,Long> quizCounts=new HashMap<>();
  for(Object[] row:em.createQuery("select i.quiz.id,count(i) from QuizQuestion i where i.quiz.course.id=:id group by i.quiz.id",Object[].class).setParameter("id",courseId).getResultList())quizCounts.put((Long)row[0],(Long)row[1]);
  for(Quiz q:quizRows) grouped.computeIfAbsent(q.getLesson()==null?null:q.getLesson().getId(),key->new ArrayList<>()).add(new QuizItem(q.getId(),q.getTitle(),q.isActive(),quizCounts.getOrDefault(q.getId(),0L).intValue(),q.getDurationMinutes(),q.getPassRate(),q.getMaxAttempts()));
  return new Outline(rows.stream().map(l->new LessonItem(l.getId(),l.getTitle(),l.getPosition(),l.isActive(),counts.getOrDefault(l.getId(),0L),grouped.getOrDefault(l.getId(),List.of()))).toList(),grouped.getOrDefault(null,List.of()),counts.getOrDefault(null,0L));
 }
 @Transactional public Outline reorder(Long courseId,String email,List<Long> ids,List<Long> expectedIds) {
  Course c=owned(courseId,email,true);
  if(c.getStatus()!=CourseStatus.DRAFT && c.getStatus()!=CourseStatus.REJECTED) throw error(HttpStatus.CONFLICT,"Chỉ sắp xếp giáo trình khi khóa học là Nháp hoặc Bị từ chối.");
  var rows=lessons.findByCourseIdOrderByPositionAscIdAsc(courseId);
  var current=rows.stream().map(Lesson::getId).toList();
  if(!current.equals(expectedIds)) throw error(HttpStatus.CONFLICT,"Giáo trình vừa thay đổi. Hãy tải lại trước khi sắp xếp.");
  if(ids==null || ids.size()!=current.size() || new HashSet<>(ids).size()!=ids.size() || !new HashSet<>(ids).equals(new HashSet<>(current))) throw error(HttpStatus.BAD_REQUEST,"Danh sách phải chứa đúng tất cả bài học của khóa, mỗi bài một lần.");
  Map<Long,Lesson> byId=new HashMap<>();
  for(int i=0;i<rows.size();i++){rows.get(i).setPosition(-i-1);byId.put(rows.get(i).getId(),rows.get(i));}
  em.flush();
  for(int i=0;i<ids.size();i++)byId.get(ids.get(i)).setPosition(i+1);
  var log=new AuditLog();log.setActor(c.getExpert());log.setAction("CURRICULUM_REORDERED");log.setTargetType("COURSE");log.setTargetId(courseId.toString());log.setMetadata(ids.toString());log.setOccurredAt(Instant.now());em.persist(log);em.flush();
  return read(courseId,email);
 }
 private Course owned(Long id,String email,boolean lock){
  Course c=(lock?courses.findForUpdate(id):courses.findById(id)).orElseThrow(()->error(HttpStatus.NOT_FOUND,"Không tìm thấy khóa học."));
  if(!c.getExpert().getEmail().equalsIgnoreCase(email)) throw error(HttpStatus.FORBIDDEN,"Khóa học không thuộc bạn.");
  return c;
 }
 private ApiException error(HttpStatus status,String message){return new ApiException(status,message);}
}

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
 private final CourseContentEditing editing;
 public record QuizItem(Long id,String title,boolean active,int questionCount,Integer durationMinutes,Integer passRate,Integer maxAttempts) {}
 public record LessonItem(Long id,String title,Integer position,boolean active,long bankQuestions,List<QuizItem> quizzes) {}
 public record QuestionItem(Long id,Long quizId,Integer position,String content,List<Long> lessonIds) {}
 public record QuestionChoice(Long id,String content,boolean active,List<Long> lessonIds) {}
 public record QuestionSelection(List<QuestionChoice> questions,List<Long> selectedIds) {}
 public record Outline(List<LessonItem> lessons,List<QuizItem> courseQuizzes,long sharedQuestions,List<QuestionItem> questions) {}
 public Outline read(Long courseId,String email) {
  owned(courseId,email,false);
  var rows=lessons.findByCourseIdOrderByPositionAscIdAsc(courseId).stream().filter(Lesson::isActive).toList();
  var quizRows=em.createQuery("select q from Quiz q where q.course.id=:id and q.active=true order by q.id",Quiz.class).setParameter("id",courseId).getResultList();

  Map<Long,List<QuizItem>> grouped=new HashMap<>();
  Map<Long,Long> quizCounts=new HashMap<>();
  for(Object[] row:em.createQuery("select i.quiz.id,count(i) from QuizQuestion i where i.quiz.course.id=:id group by i.quiz.id",Object[].class).setParameter("id",courseId).getResultList())quizCounts.put((Long)row[0],(Long)row[1]);
  for(Quiz q:quizRows) grouped.computeIfAbsent(q.getLesson()==null?null:q.getLesson().getId(),key->new ArrayList<>()).add(new QuizItem(q.getId(),q.getTitle(),q.isActive(),quizCounts.getOrDefault(q.getId(),0L).intValue(),q.getDurationMinutes(),q.getPassRate(),q.getMaxAttempts()));
  var assignments=courseQuestions(courseId);
  var links=em.createQuery("select i from QuizQuestion i where i.quiz.course.id=:id order by i.quiz.id,i.position,i.id",QuizQuestion.class).setParameter("id",courseId).getResultList();
  Map<Long,QuizQuestion> byQuestion=new HashMap<>();
  links.forEach(i->byQuestion.putIfAbsent(i.getQuestion().getId(),i));
  var questionRows=assignments.stream().map(q->{var link=byQuestion.get(q.getId());return new QuestionItem(q.getId(),link==null?null:link.getQuiz().getId(),link==null?null:link.getPosition(),q.getContent(),lessonIds(q));}).toList();
  return new Outline(rows.stream().map(l->new LessonItem(l.getId(),l.getTitle(),l.getPosition(),l.isActive(),questionRows.stream().filter(q->q.lessonIds().contains(l.getId())).count(),grouped.getOrDefault(l.getId(),List.of()))).toList(),grouped.getOrDefault(null,List.of()),questionRows.stream().filter(q->q.lessonIds().isEmpty()).count(),questionRows);
 }
 @Transactional public Outline reorder(Long courseId,String email,List<Long> ids,List<Long> expectedIds) {
  Course c=owned(courseId,email,true);
  if(c.getStatus()!=CourseStatus.DRAFT && c.getStatus()!=CourseStatus.REJECTED) throw error(HttpStatus.CONFLICT,"Chỉ sắp xếp giáo trình khi khóa học là Nháp hoặc Bị từ chối.");
  var rows=lessons.findByCourseIdOrderByPositionAscIdAsc(courseId).stream().filter(Lesson::isActive).toList();
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

 public QuestionSelection lessonQuestions(Long courseId,Long lessonId,String email) {
  owned(courseId,email,false);requireLesson(courseId,lessonId);
  return selection(courseId,lessonId);
 }
 @Transactional public QuestionSelection saveLessonQuestions(Long courseId,Long lessonId,String email,List<Long> ids,List<Long> expectedIds) {
  Course course=owned(courseId,email,true);
  if(course.getStatus()!=CourseStatus.DRAFT && course.getStatus()!=CourseStatus.REJECTED)
   throw error(HttpStatus.CONFLICT,"Chỉ chọn câu hỏi khi khóa học là Nháp hoặc Bị từ chối.");
  Lesson lesson=requireLesson(courseId,lessonId);
  if(!lesson.isActive()) throw error(HttpStatus.CONFLICT,"Bài học đang bị ẩn, không thể thay đổi câu hỏi.");
  var questions=courseQuestions(courseId);
  Set<Long> current=new HashSet<>();
  questions.stream().filter(q->q.getLessons().stream().anyMatch(l->l.getId().equals(lessonId))).forEach(q->current.add(q.getId()));
  if(expectedIds==null || new HashSet<>(expectedIds).size()!=expectedIds.size() || !current.equals(new HashSet<>(expectedIds)))
   throw error(HttpStatus.CONFLICT,"Danh sách câu hỏi vừa thay đổi. Đóng hộp thoại và mở lại trước khi lưu.");
  if(ids==null || new HashSet<>(ids).size()!=ids.size()) throw error(HttpStatus.BAD_REQUEST,"Mỗi câu hỏi chỉ được chọn một lần trong một bài.");
  Set<Long> selected=new HashSet<>(ids);
  Map<Long,Question> available=new HashMap<>();questions.forEach(q->available.put(q.getId(),q));
  if(selected.stream().anyMatch(id->!available.containsKey(id))) throw error(HttpStatus.BAD_REQUEST,"Câu hỏi không thuộc khóa học này.");
  if(selected.stream().anyMatch(id->!current.contains(id) && !available.get(id).isActive())) throw error(HttpStatus.CONFLICT,"Không thể thêm câu hỏi đã ngừng hoạt động.");
  for(Question question:questions) {
   if(selected.contains(question.getId()) && !current.contains(question.getId())) question.getLessons().add(lesson);
   else if(!selected.contains(question.getId()) && current.contains(question.getId())) question.getLessons().removeIf(l->l.getId().equals(lessonId));
   if(question.getLesson()!=null && question.getLesson().getId().equals(lessonId)) question.setLesson(null);
  }
  var log=new AuditLog();log.setActor(course.getExpert());log.setAction("LESSON_QUESTIONS_UPDATED");log.setTargetType("LESSON");log.setTargetId(lessonId.toString());log.setMetadata("selectedCount="+selected.size()+", previousCount="+current.size());log.setOccurredAt(Instant.now());em.persist(log);em.flush();
  return selection(courseId,lessonId);
 }
 private QuestionSelection selection(Long courseId,Long lessonId) {
  var questions=courseQuestions(courseId).stream().map(q->new QuestionChoice(q.getId(),q.getContent(),q.isActive(),lessonIds(q))).toList();
  return new QuestionSelection(questions,questions.stream().filter(q->q.lessonIds().contains(lessonId)).map(QuestionChoice::id).toList());
 }
 private List<Question> courseQuestions(Long courseId) {
  return em.createQuery("select distinct q from Question q left join fetch q.lessons where q.course.id=:id and (q.status is null or q.status<>'RETIRED') order by q.id",Question.class).setParameter("id",courseId).getResultList();
 }
 private List<Long> lessonIds(Question q) {return q.getLessons().stream().map(Lesson::getId).sorted().toList();}
 private Lesson requireLesson(Long courseId,Long lessonId) {
  return lessons.findByIdAndCourseId(lessonId,courseId).orElseThrow(()->error(HttpStatus.NOT_FOUND,"Bài học không thuộc khóa học này."));
 }
 private Course owned(Long id,String email,boolean lock){
  Course c=(lock?courses.findForUpdate(id):courses.findById(id)).orElseThrow(()->error(HttpStatus.NOT_FOUND,"Không tìm thấy khóa học."));
  if(!c.getExpert().getEmail().equalsIgnoreCase(email)) throw error(HttpStatus.FORBIDDEN,"Khóa học không thuộc bạn.");
  if(lock) editing.begin(c);
  return c;
 }
 private ApiException error(HttpStatus status,String message){return new ApiException(status,message);}
}

package com.onlinelearning.service;

import com.onlinelearning.entity.*;
import com.onlinelearning.repository.*;
import com.onlinelearning.exception.ApiException;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.util.*;

@Service @RequiredArgsConstructor @Transactional(readOnly=true)
public class LessonDiscussionService {
 private final com.onlinelearning.service.NotificationEvents notificationEvents;
    private final UserRepository users;
 private final LessonRepository lessons;
 private final CourseRegistrationRepository registrations;
 private final LessonDiscussionRepository discussions;
 private final LessonDiscussionAnswerRepository answers;
 private final EntityManager em;
 public record QuestionView(Long id,Long courseId,String courseTitle,Long lessonId,String lessonTitle,
   Long authorId,String authorName,String title,String content,Instant createdAt,boolean hidden,
   Long acceptedAnswerId,boolean canResolve,boolean canModerate,boolean canReply) {}
 public record AnswerView(Long id,Long authorId,String authorName,String content,Instant createdAt,boolean hidden,boolean expert) {}
 public record Detail(QuestionView question,Page<AnswerView> answers) {}

 public Page<QuestionView> list(String email,Long lessonId,String search,String state,int page) {
  User actor=actor(email);boolean staff=moderator(actor);
  if(lessonId!=null) access(actor,lesson(lessonId));
  else if(!staff&&!has(actor,"EXPERT")) throw forbidden();
  String term=search==null?"":search.strip();
  if(term.length()>100||!Set.of("all","unanswered","resolved","open").contains(state))throw bad("Bộ lọc không hợp lệ.");
  return discussions.findAll((root,query,cb)->{
   List<Predicate> filters=new ArrayList<>();
   if(lessonId!=null)filters.add(cb.equal(root.get("lesson").get("id"),lessonId));
   else if(!staff)filters.add(cb.equal(root.get("lesson").get("course").get("expert").get("id"),actor.getId()));
   if(!staff)filters.add(cb.isFalse(root.get("hidden")));
   if(!term.isEmpty()){
    String pattern="%"+term.toLowerCase(Locale.ROOT).replace("!","!!").replace("%","!%").replace("_","!_")+"%";
    filters.add(cb.or(cb.like(cb.lower(root.get("title")),pattern,'!'),cb.like(cb.lower(root.get("content")),pattern,'!')));
   }
   if(state.equals("resolved"))filters.add(cb.isNotNull(root.get("acceptedAnswer")));
   if(state.equals("open"))filters.add(cb.isNull(root.get("acceptedAnswer")));
   if(state.equals("unanswered")){
    var sub=query.subquery(Long.class);var answer=sub.from(LessonDiscussionAnswer.class);
    sub.select(answer.get("id")).where(cb.equal(answer.get("discussion"),root),cb.isFalse(answer.get("hidden")));
    filters.add(cb.not(cb.exists(sub)));
   }
   return cb.and(filters.toArray(Predicate[]::new));
  },paging(page)).map(q->view(q,actor));
 }
 public Detail detail(String email,Long id,int page){
  User actor=actor(email);var q=question(actor,id);
  var result=moderator(actor)?answers.findByDiscussionId(id,paging(page)):answers.findByDiscussionIdAndHiddenFalse(id,paging(page));
  return new Detail(view(q,actor),result.map(a->answerView(a,q)));
 }
 @Transactional public QuestionView create(String email,Long lessonId,String title,String content){
  User actor=actor(email);Lesson lesson=lesson(lessonId);access(actor,lesson);
  if(!has(actor,"STUDENT")||registrations.countCurrentAccess(actor.getId(),lesson.getCourse().getId(),Instant.now())==0)throw forbidden();
  if(!lesson.isActive()||lesson.getCourse().getStatus()!=CourseStatus.PUBLISHED)throw missing();
  var q=new LessonDiscussion();q.setLesson(lesson);q.setAuthor(actor);q.setTitle(text(title,200));q.setContent(text(content,4000));
  discussions.saveAndFlush(q); notificationEvents.questionCreated(q); return view(q,actor);
 }
 @Transactional public AnswerView reply(String email,Long id,String content){
  User actor=actor(email);var q=question(actor,id);lock(q);
  if(q.isHidden()||!canReply(actor,q.getLesson()))throw forbidden();
  var a=new LessonDiscussionAnswer();a.setDiscussion(q);a.setAuthor(actor);a.setContent(text(content,4000));
  answers.saveAndFlush(a); notificationEvents.questionAnswered(a); return answerView(a,q);
 }
 @Transactional public void resolve(String email,Long id,Long answerId){
  User actor=actor(email);var q=question(actor,id);lock(q);
  if(q.isHidden()||!canResolve(actor,q))throw forbidden();
  if(answerId==null){q.setAcceptedAnswer(null);return;}
  var a=answers.findById(answerId).filter(v->v.getDiscussion().getId().equals(id)&&!v.isHidden()).orElseThrow(()->bad("Câu trả lời không hợp lệ."));
  q.setAcceptedAnswer(a);
 }
 @Transactional public void hideQuestion(String email,Long id,boolean hidden){
  User actor=actor(email);if(!moderator(actor))throw forbidden();
  var q=question(actor,id);lock(q);q.setHidden(hidden);
 }
 @Transactional public void hideAnswer(String email,Long id,Long answerId,boolean hidden){
  User actor=actor(email);if(!moderator(actor))throw forbidden();
  var q=question(actor,id);lock(q);
  var a=answers.findById(answerId).filter(v->v.getDiscussion().getId().equals(id)).orElseThrow(()->missing());
  a.setHidden(hidden);
  if(hidden&&q.getAcceptedAnswer()!=null&&q.getAcceptedAnswer().getId().equals(answerId))q.setAcceptedAnswer(null);
 }
 private void lock(LessonDiscussion q){em.lock(q,LockModeType.PESSIMISTIC_WRITE);em.refresh(q);}
 private LessonDiscussion question(User actor,Long id){
  var q=discussions.findById(id).orElseThrow(()->missing());access(actor,q.getLesson());
  if(q.isHidden()&&!moderator(actor))throw missing();return q;
 }
 private Lesson lesson(Long id){return lessons.findById(id).orElseThrow(()->missing());}
 private void access(User actor,Lesson l){
  if(moderator(actor)||owner(actor,l))return;
  if(!has(actor,"STUDENT"))throw forbidden();
  if(!l.isActive()||l.getCourse().getStatus()!=CourseStatus.PUBLISHED)throw missing();
  if(registrations.countCurrentAccess(actor.getId(),l.getCourse().getId(),Instant.now())==0)throw forbidden();
 }
 private boolean owner(User u,Lesson l){return has(u,"EXPERT")&&l.getCourse().getExpert().getId().equals(u.getId());}
 private boolean canReply(User u,Lesson l){return owner(u,l);}
 private boolean canResolve(User u,LessonDiscussion q){return u.getId().equals(q.getAuthor().getId())||owner(u,q.getLesson());}
 private boolean moderator(User u){return has(u,"ADMIN")||has(u,"MANAGER");}
 private boolean has(User u,String role){return u.getRoles().stream().anyMatch(r->role.equals(r.getCode()));}
 private User actor(String email){return users.findByEmailIgnoreCase(email).filter(u->u.isEnabled()&&!u.isBlocked()).orElseThrow(()->forbidden());}
 private QuestionView view(LessonDiscussion q,User actor){
  var l=q.getLesson();return new QuestionView(q.getId(),l.getCourse().getId(),l.getCourse().getTitle(),l.getId(),l.getTitle(),
   q.getAuthor().getId(),q.getAuthor().getFullName(),q.getTitle(),q.getContent(),q.getCreatedAt(),q.isHidden(),
   q.getAcceptedAnswer()==null?null:q.getAcceptedAnswer().getId(),!q.isHidden()&&canResolve(actor,q),moderator(actor),!q.isHidden()&&canReply(actor,l));
 }
 private AnswerView answerView(LessonDiscussionAnswer a,LessonDiscussion q){return new AnswerView(a.getId(),a.getAuthor().getId(),a.getAuthor().getFullName(),a.getContent(),a.getCreatedAt(),a.isHidden(),owner(a.getAuthor(),q.getLesson()));}
 private Pageable paging(int page){if(page<0||page>100000)throw bad("Trang không hợp lệ.");return PageRequest.of(page,20,Sort.by(Sort.Direction.DESC,"id"));}
 private String text(String value,int limit){if(value==null||value.isBlank()||value.strip().length()>limit)throw bad("Nội dung trống hoặc vượt giới hạn ký tự.");return value.strip();}
 private ApiException forbidden(){return new ApiException(HttpStatus.FORBIDDEN,"Bạn không có quyền truy cập hỏi đáp này hoặc quyền học đã hết hạn.");}
 private ApiException missing(){return new ApiException(HttpStatus.NOT_FOUND,"Không tìm thấy bài học hoặc câu hỏi.");}
 private ApiException bad(String message){return new ApiException(HttpStatus.BAD_REQUEST,message);}
}
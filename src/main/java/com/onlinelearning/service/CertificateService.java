package com.onlinelearning.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.onlinelearning.entity.*;
import com.onlinelearning.repository.*;
import com.onlinelearning.exception.ApiException;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.*;

@Service @RequiredArgsConstructor @Transactional(readOnly=true)
public class CertificateService {
 private static final String CODE_ALPHABET="0123456789ABCDEFGHJKMNPQRSTVWXYZ";
 private static final SecureRandom CODE_RANDOM=new SecureRandom();
 private final UserRepository users;
 private final CourseRepository courses;
 private final CourseRegistrationRepository registrations;
 private final CertificatePolicyRepository policies;
 private final CourseCertificateRepository certificates;
 private final CertificateAuditLogRepository audit;
 private final EntityManager em;
 private final NotificationService notifications;
 private final ObjectMapper json;

 public record Item(Long id,String title,boolean complete) {}
 public record View(Long id,Long courseId,String code,String status,String studentName,String courseTitle,
  String expertName,String issuerName,int policyVersion,Instant issuedAt,Instant revokedAt,String revocationReason) {}
 public record PublicView(String code,String status,String studentName,String courseTitle,String issuerName,Instant issuedAt,Instant revokedAt) {}
 public record Eligibility(Long courseId,String courseTitle,boolean enabled,boolean eligible,
  List<String> reasons,List<Item> lessons,List<Item> quizzes,View certificate) {}
 public record PolicyView(Long id,int version,boolean enabled,String issuerName,String configuredBy,Instant createdAt,List<Item> lessons,List<Item> quizzes) {}
 public record PolicyOptions(Long courseId,String courseTitle,String courseStatus,List<Item> lessons,List<Item> quizzes,PolicyView current,List<PolicyView> history) {}
 public record CourseOption(Long id,String title,String status,String expertName) {}
 public record AuditView(Long id,String action,String actorName,String reason,Instant occurredAt) {}

 public Page<View> mine(String email,String search,String status,int page){
  User u=student(email);
  return certificates.findAll(spec(search,status,null,u.getId()),paging(page,12)).map(this::view);
 }
 public View mineDetail(String email,Long id){return view(owned(student(email),id));}
 public Page<View> management(String email,String search,String status,Long courseId,int page){
  manager(email);return certificates.findAll(spec(search,status,courseId,null),paging(page,20)).map(this::view);
 }
 public View managementDetail(String email,Long id){manager(email);return view(certificate(id));}
 public PublicView verify(String code){
  String normalized=code==null?"":code.strip().toUpperCase(Locale.ROOT);
  if(!normalized.matches("[0-9A-HJKMNP-TV-Z]{8}|[0-9A-F]{32}"))throw missing();
  var c=certificates.findByCode(normalized).orElseThrow(this::missing);
  return new PublicView(c.getCode(),c.getStatus(),c.getStudentNameSnapshot(),c.getCourseTitleSnapshot(),c.getIssuerNameSnapshot(),c.getIssuedAt(),c.getRevokedAt());
 }
 public Page<Eligibility> myCourses(String email,String search,int page){
  User u=student(email);String term=term(search);
  String pattern=term.isEmpty()?"":"%"+term.toLowerCase(Locale.ROOT).replace("\\","\\\\").replace("%","\\%").replace("_","\\_")+"%";
  return registrations.findCurrentCourses(u.getId(),Instant.now(),pattern,paging(page,12)).map(r->inspect(u,r.getCourse()));
 }
 @Transactional public Eligibility checkAndIssue(String email,Long courseId){
  User u=student(email);em.lock(u,LockModeType.PESSIMISTIC_WRITE);
  Course c=courses.findForUpdate(courseId).orElseThrow(this::missing);
  if(em.createQuery("select count(r) from CourseRegistration r where r.student.id=:student and r.course.id=:course",Long.class)
   .setParameter("student",u.getId()).setParameter("course",courseId).getSingleResult()==0)throw missing();
  return issue(u,c);
 }
 // Called by lesson completion / quiz grading in their existing transaction.
 @Transactional public void tryIssue(Long studentId,Long courseId){
  User u=users.findById(studentId).orElseThrow(this::missing);
  if(!active(u)||!role(u,"STUDENT"))return;
  em.lock(u,LockModeType.PESSIMISTIC_WRITE);
  Course c=courses.findForUpdate(courseId).orElseThrow(this::missing);
  issue(u,c);
 }
 private Eligibility issue(User u,Course course){
  var existing=certificates.findForIssue(u.getId(),course.getId()).orElse(null);
  if(existing!=null)return new Eligibility(course.getId(),course.getTitle(),true,false,List.of(existing.getStatus().equals("REVOKED")?"Chứng nhận đã bị thu hồi.":"Chứng nhận đã được cấp."),List.of(),List.of(),view(existing));
  Eligibility check=inspect(u,course);
  if(check.certificate()!=null||!check.eligible())return check;
  CertificatePolicy p=policies.findFirstByCourseIdOrderByVersionDesc(course.getId()).orElseThrow();
  var reg=registrations.findAccessRegistration(u.getId(),course.getId(),Instant.now(),PageRequest.of(0,1)).getFirst();
  var cert=new CourseCertificate();cert.setStudent(u);cert.setCourse(course);cert.setRegistration(reg);cert.setPolicy(p);
  cert.setCode(newCode());
  cert.setStudentNameSnapshot(u.getFullName());cert.setCourseTitleSnapshot(course.getTitle());
  cert.setExpertNameSnapshot(course.getExpert().getFullName());cert.setIssuerNameSnapshot(p.getIssuerName());
  var evidence=new LinkedHashMap<String,Object>();evidence.put("policyId",p.getId());evidence.put("policyVersion",p.getVersion());
  evidence.put("lessons",check.lessons().stream().map(Item::id).toList());
  evidence.put("quizAttempts",em.createQuery("select a.id from QuizAttempt a where a.student.id=:student and a.quiz.id in :ids and a.passed=true and a.submittedAt is not null order by a.id",Long.class)
   .setParameter("student",u.getId()).setParameter("ids",p.getRequiredQuizzes().keySet()).getResultList());
  cert.setEvidenceSnapshot(serialize(evidence));certificates.saveAndFlush(cert);
  record(cert,u,"ISSUED",null);
  notifications.send(u.getId(),"certificate-issued:"+cert.getId(),"CERTIFICATE_ISSUED","CERTIFICATE","Bạn đã nhận chứng nhận hoàn thành",
   course.getTitle()+" · "+p.getIssuerName(),"/certificates?certificate="+cert.getId());
  return new Eligibility(check.courseId(),check.courseTitle(),true,true,List.of(),check.lessons(),check.quizzes(),view(cert));
 }
 private String newCode(){
  for(int attempt=0;attempt<10;attempt++){
   var code=new StringBuilder(8);
   for(int i=0;i<8;i++)code.append(CODE_ALPHABET.charAt(CODE_RANDOM.nextInt(CODE_ALPHABET.length())));
   String candidate=code.toString();
   if(!certificates.existsByCode(candidate))return candidate;
  }
  throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE,"Không thể tạo mã chứng chỉ. Vui lòng thử lại.");
 }
 private Eligibility inspect(User u,Course c){
  var prior=certificates.findByStudentIdAndCourseId(u.getId(),c.getId()).orElse(null);
  if(prior!=null)return new Eligibility(c.getId(),c.getTitle(),true,false,
   List.of(prior.getStatus().equals("REVOKED")?"Chứng nhận đã bị thu hồi. Liên hệ quản lý để được hỗ trợ.":"Chứng nhận đã được cấp."),
   List.of(),List.of(),view(prior));
  CertificatePolicy p=policies.findFirstByCourseIdOrderByVersionDesc(c.getId()).orElse(null);
  if(p==null||!p.isEnabled())return new Eligibility(c.getId(),c.getTitle(),false,false,List.of("Khóa học chưa bật cấp chứng nhận."),List.of(),List.of(),null);
  List<String> reasons=new ArrayList<>();
  if(c.getStatus()!=CourseStatus.PUBLISHED)reasons.add("Khóa học hiện chưa được xuất bản.");
  if(registrations.countCurrentAccess(u.getId(),c.getId(),Instant.now())==0)reasons.add("Bạn cần quyền học còn hiệu lực để được cấp lần đầu.");
  List<Lesson> currentLessons=activeLessons(c.getId());
  List<Quiz> currentQuizzes=quizRows(c.getId()).stream().filter(q->p.getRequiredQuizzes().containsKey(q.getId())).toList();
  if(p.getRequiredLessons().isEmpty()||p.getRequiredQuizzes().isEmpty()
    ||currentQuizzes.stream().anyMatch(q->!ready(q))
    ||!Objects.equals(p.getContentFingerprint(),fingerprint(currentLessons,currentQuizzes)))
   reasons.add("Nội dung khóa học đã thay đổi. Quản lý cần cập nhật điều kiện chứng nhận.");
  Set<Long> completed=new HashSet<>(em.createQuery("select p.lesson.id from StudentLessonProgress p where p.student.id=:student and p.lesson.course.id=:course and p.completed=true and p.completedAt is not null",Long.class)
   .setParameter("student",u.getId()).setParameter("course",c.getId()).getResultList());
  Set<Long> passed=new HashSet<>(em.createQuery("select distinct a.quiz.id from QuizAttempt a where a.student.id=:student and a.quiz.course.id=:course and a.passed=true and a.submittedAt is not null",Long.class)
   .setParameter("student",u.getId()).setParameter("course",c.getId()).getResultList());
  List<Item> lessonChecks=items(p.getRequiredLessons(),completed),quizChecks=items(p.getRequiredQuizzes(),passed);
  if(lessonChecks.stream().anyMatch(i->!i.complete()))reasons.add("Chưa hoàn thành tất cả bài học bắt buộc.");
  if(quizChecks.stream().anyMatch(i->!i.complete()))reasons.add("Chưa đạt tất cả quiz bắt buộc.");
  return new Eligibility(c.getId(),c.getTitle(),true,reasons.isEmpty(),reasons,lessonChecks,quizChecks,null);
 }

 public Page<CourseOption> courseOptions(String email,String search,int page,boolean eligibleOnly){
  manager(email);String term=term(search);
  Specification<Course> spec=(r,q,cb)->term.isEmpty()?cb.conjunction():cb.like(cb.lower(r.get("title")),pattern(term),'!');
  Pageable pageable=paging(page,10);
  if(!eligibleOnly)return courses.findAll(spec,pageable).map(this::courseOption);

  Specification<Course> published=(r,q,cb)->cb.equal(r.get("status"),CourseStatus.PUBLISHED);
  List<Course> eligible=courses.findAll(spec.and(published),pageable.getSort()).stream()
   .filter(c->!activeLessons(c.getId()).isEmpty()&&quizRows(c.getId()).stream().anyMatch(this::ready))
   .toList();
  List<CourseOption> options=eligible.stream().skip(pageable.getOffset()).limit(pageable.getPageSize())
   .map(this::courseOption).toList();
  return new PageImpl<>(options,pageable,eligible.size());
 }
 private CourseOption courseOption(Course c){
  return new CourseOption(c.getId(),c.getTitle(),c.getStatus().name(),c.getExpert().getFullName());
 }
 public PolicyOptions policy(String email,Long courseId){
  manager(email);Course c=courses.findById(courseId).orElseThrow(this::missing);
  var current=policies.findFirstByCourseIdOrderByVersionDesc(courseId).orElse(null);
  return new PolicyOptions(courseId,c.getTitle(),c.getStatus().name(),
   activeLessons(courseId).stream().map(l->new Item(l.getId(),l.getTitle(),true)).toList(),
   quizRows(courseId).stream().map(q->new Item(q.getId(),q.getTitle(),ready(q))).toList(),
   current==null?null:policyView(current),policies.findTop10ByCourseIdOrderByVersionDesc(courseId).stream().map(this::policyView).toList());
 }
 @Transactional public PolicyView savePolicy(String email,Long courseId,boolean enabled,String issuer,List<Long> quizIds,Integer expectedVersion){
  User actor=manager(email);Course c=courses.findForUpdate(courseId).orElseThrow(this::missing);
  var current=policies.findFirstByCourseIdOrderByVersionDesc(courseId).orElse(null);
  int version=current==null?0:current.getVersion();
  if(expectedVersion==null||expectedVersion!=version)throw error(HttpStatus.CONFLICT,"Điều kiện vừa được người khác cập nhật. Hãy tải lại.");
  String issuerName=text(issuer,160);
  if(quizIds==null||quizIds.size()>1000||new HashSet<>(quizIds).size()!=quizIds.size()||quizIds.stream().anyMatch(Objects::isNull))throw error(HttpStatus.BAD_REQUEST,"Danh sách quiz không hợp lệ.");
  List<Lesson> lessons=activeLessons(courseId);
  List<Quiz> selected=quizRows(courseId).stream().filter(q->quizIds.contains(q.getId())).toList();
  if(enabled&&(c.getStatus()!=CourseStatus.PUBLISHED||lessons.isEmpty()||selected.isEmpty()||selected.size()!=quizIds.size()||selected.stream().anyMatch(q->!ready(q))))
   throw error(HttpStatus.BAD_REQUEST,"Cần khóa đã xuất bản, ít nhất một bài học và một quiz đã cấu hình hợp lệ.");
  var p=new CertificatePolicy();p.setCourse(c);p.setVersion(version+1);p.setEnabled(enabled);p.setIssuerName(issuerName);p.setConfiguredBy(actor);
  lessons.forEach(l->p.getRequiredLessons().put(l.getId(),l.getTitle()));
  selected.forEach(q->p.getRequiredQuizzes().put(q.getId(),q.getTitle()));
  p.setContentFingerprint(fingerprint(lessons,selected));
  policies.saveAndFlush(p);return policyView(p);
 }
 public Page<AuditView> history(String email,Long id,int page){
  manager(email);certificate(id);
  return audit.findByCertificateIdOrderByIdDesc(id,paging(page,10)).map(a->new AuditView(a.getId(),a.getAction(),a.getActor().getFullName(),a.getReason(),a.getOccurredAt()));
 }
 @Transactional public View revoke(String email,Long id,String reason){
  User actor=manager(email);String explanation=text(reason,500);
  CourseCertificate c=certificate(id);em.lock(c,LockModeType.PESSIMISTIC_WRITE);em.refresh(c);
  if(c.getStatus().equals("REVOKED"))return view(c);
  c.setStatus("REVOKED");c.setRevokedAt(Instant.now());c.setRevocationReason(explanation);
  record(c,actor,"REVOKED",explanation);em.flush();
  notifications.send(c.getStudent().getId(),"certificate-revoked:"+id,"CERTIFICATE_REVOKED","CERTIFICATE","Chứng nhận đã bị thu hồi",
   c.getCourseTitleSnapshot()+". Lý do: "+explanation,"/certificates?certificate="+id);
  return view(c);
 }
 private Specification<CourseCertificate> spec(String search,String status,Long courseId,Long studentId){
  String term=term(search);
  if(!Set.of("all","ISSUED","REVOKED").contains(status))throw error(HttpStatus.BAD_REQUEST,"Trạng thái không hợp lệ.");
  return (r,q,cb)->{
   var conditions=new ArrayList<jakarta.persistence.criteria.Predicate>();
   if(studentId!=null)conditions.add(cb.equal(r.get("student").get("id"),studentId));
   if(courseId!=null)conditions.add(cb.equal(r.get("course").get("id"),courseId));
   if(!status.equals("all"))conditions.add(cb.equal(r.get("status"),status));
   if(!term.isEmpty())conditions.add(cb.or(cb.like(cb.lower(r.get("studentNameSnapshot")),pattern(term),'!'),cb.like(cb.lower(r.get("courseTitleSnapshot")),pattern(term),'!'),cb.like(cb.lower(r.get("code")),pattern(term),'!')));
   return cb.and(conditions.toArray(jakarta.persistence.criteria.Predicate[]::new));
  };
 }
 private List<Lesson> activeLessons(Long id){return em.createQuery("select l from Lesson l where l.course.id=:id and l.active=true order by l.id",Lesson.class).setParameter("id",id).getResultList();}
 private List<Quiz> quizRows(Long id){return em.createQuery("select q from Quiz q where q.course.id=:id order by q.id",Quiz.class).setParameter("id",id).getResultList();}
 private boolean ready(Quiz q){
  if(!q.isActive()||(q.getLesson()!=null&&!q.getLesson().isActive())||q.getDurationMinutes()==null||q.getDurationMinutes()<1||q.getPassRate()==null||q.getPassRate()<0||q.getPassRate()>100||q.getMaxAttempts()==null||q.getMaxAttempts()<1||q.getQuizQuestions().isEmpty())return false;
  return q.getQuizQuestions().stream().allMatch(link->{
   Question question=link.getQuestion();long correct=question.getOptions().stream().filter(QuestionOption::isCorrect).count();
   return question.isActive()&&Set.of("SINGLE_CHOICE","MULTIPLE_CHOICE").contains(Objects.toString(question.getType(),""))
    &&question.getOptions().size()>=2&&correct>0&&(!"SINGLE_CHOICE".equals(question.getType())||correct==1)&&link.getPoints()!=null&&link.getPoints()>0&&link.getPosition()!=null;
  });
 }
 private String fingerprint(List<Lesson> lessons,List<Quiz> quizzes){
  var data=new ArrayList<String>();
  lessons.stream().sorted(Comparator.comparing(Lesson::getId)).forEach(l->data.add(serialize(Arrays.asList("lesson",l.getId(),l.getTitle(),l.getContent(),l.getVideo()==null?null:l.getVideo().getId()))));
  quizzes.stream().sorted(Comparator.comparing(Quiz::getId)).forEach(q->{
   data.add(serialize(Arrays.asList("quiz",q.getId(),q.getTitle(),q.isActive(),q.getPassRate(),q.getDurationMinutes(),q.getMaxAttempts(),q.getLesson()==null?null:q.getLesson().getId())));
   q.getQuizQuestions().stream().sorted(Comparator.comparing(QuizQuestion::getId)).forEach(link->{
    var question=link.getQuestion();data.add(serialize(Arrays.asList(link.getId(),link.getPoints(),link.getPosition(),question.getId(),question.getContent(),question.getType(),question.isActive())));
    question.getOptions().stream().sorted(Comparator.comparing(QuestionOption::getId)).forEach(o->data.add(serialize(Arrays.asList(o.getId(),o.getContent(),o.isCorrect()))));
   });
  });
  try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(serialize(data).getBytes(StandardCharsets.UTF_8)));}
  catch(java.security.NoSuchAlgorithmException e){throw new IllegalStateException(e);}
 }
 private List<Item> items(Map<Long,String> map,Set<Long> complete){return map.entrySet().stream().sorted(Map.Entry.comparingByKey()).map(e->new Item(e.getKey(),e.getValue(),complete.contains(e.getKey()))).toList();}
 private PolicyView policyView(CertificatePolicy p){return new PolicyView(p.getId(),p.getVersion(),p.isEnabled(),p.getIssuerName(),p.getConfiguredBy().getFullName(),p.getCreatedAt(),items(p.getRequiredLessons(),p.getRequiredLessons().keySet()),items(p.getRequiredQuizzes(),p.getRequiredQuizzes().keySet()));}
 private void record(CourseCertificate c,User u,String action,String reason){var entry=new CertificateAuditLog();entry.setCertificate(c);entry.setActor(u);entry.setAction(action);entry.setReason(reason);audit.save(entry);}
 private View view(CourseCertificate c){return new View(c.getId(),c.getCourse().getId(),c.getCode(),c.getStatus(),c.getStudentNameSnapshot(),c.getCourseTitleSnapshot(),c.getExpertNameSnapshot(),c.getIssuerNameSnapshot(),c.getPolicy().getVersion(),c.getIssuedAt(),c.getRevokedAt(),c.getRevocationReason());}
 private CourseCertificate owned(User u,Long id){var c=certificate(id);if(!c.getStudent().getId().equals(u.getId()))throw missing();return c;}
 private CourseCertificate certificate(Long id){return certificates.findById(id).orElseThrow(this::missing);}
 private User student(String email){User u=account(email);if(!role(u,"STUDENT"))throw error(HttpStatus.FORBIDDEN,"Chỉ học viên được thực hiện thao tác này.");return u;}
 private User manager(String email){User u=account(email);if(!role(u,"ADMIN")&&!role(u,"MANAGER"))throw error(HttpStatus.FORBIDDEN,"Chỉ Admin/Manager được quản lý chứng nhận.");return u;}
 private User account(String email){return users.findByEmailIgnoreCase(email).filter(this::active).orElseThrow(()->error(HttpStatus.FORBIDDEN,"Tài khoản không khả dụng."));}
 private boolean active(User u){return u.isEnabled()&&!u.isBlocked();}
 private boolean role(User u,String code){return u.getRoles().stream().anyMatch(r->code.equals(r.getCode()));}
 private Pageable paging(int page,int size){if(page<0||page>100000)throw error(HttpStatus.BAD_REQUEST,"Trang không hợp lệ.");return PageRequest.of(page,size,Sort.by(Sort.Direction.DESC,"id"));}
 private String term(String value){if(value==null)return "";if(value.length()>100)throw error(HttpStatus.BAD_REQUEST,"Từ khóa tối đa 100 ký tự.");return value.strip();}
 private String pattern(String term){return "%"+term.toLowerCase(Locale.ROOT).replace("!","!!").replace("%","!%").replace("_","!_")+"%";}
 private String text(String value,int max){if(value==null||value.isBlank()||value.strip().length()>max)throw error(HttpStatus.BAD_REQUEST,"Nội dung trống hoặc vượt giới hạn ký tự.");return value.strip();}
 private String serialize(Object value){try{return json.writeValueAsString(value);}catch(com.fasterxml.jackson.core.JsonProcessingException e){throw new IllegalStateException(e);}}
 private ApiException missing(){return error(HttpStatus.NOT_FOUND,"Không tìm thấy chứng nhận hoặc khóa học.");}
 private ApiException error(HttpStatus status,String message){return new ApiException(status,message);}
}

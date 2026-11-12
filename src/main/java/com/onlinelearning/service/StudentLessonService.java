package com.onlinelearning.service;

import com.onlinelearning.entity.*;
import com.onlinelearning.exception.ApiException;
import com.onlinelearning.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.util.*;

@Service @RequiredArgsConstructor @Transactional(readOnly=true)
public class StudentLessonService {
    private final CertificateService certificateService;
    private final UserRepository users;
    private final CourseRepository courses;
    private final LessonRepository lessons;
    private final CourseRegistrationRepository registrations;
    private final StudentLessonProgressRepository progress;
    private final jakarta.persistence.EntityManager em;
    private final LessonAssessmentService lessonAssessments;
    public record LessonItem(Long id,String title,Integer position,boolean completed) {}
    public record LessonContent(Long id,String title,String content,Long videoMediaId) {}
    public record LessonQuiz(Long id,Long lessonId,String title,Integer durationMinutes,Integer passRate,int questionCount,boolean passed) {}
    public record LearningCourse(Long courseId,String courseTitle,String expertName,int completedLessons,int progressPercent,
        List<LessonItem> lessons,LessonContent currentLesson,List<LessonQuiz> quizzes) {}

    public LearningCourse read(Long courseId,Long lessonId,String email) {
        User student=student(email,false);Course course=accessible(courseId,student);
        var outline=lessons.findByCourseIdOrderByPositionAscIdAsc(courseId).stream().filter(Lesson::isActive).toList();
        Set<Long> completed=new HashSet<>();progress.findByStudentIdAndLessonCourseId(student.getId(),courseId).stream().filter(StudentLessonProgress::isCompleted).forEach(p->completed.add(p.getLesson().getId()));
        Lesson selected=lessonId==null ? outline.stream().filter(l->!completed.contains(l.getId())).findFirst().orElse(outline.isEmpty()?null:outline.getFirst()) : outline.stream().filter(l->l.getId().equals(lessonId)).findFirst().orElseThrow(()->new ApiException(HttpStatus.NOT_FOUND,"Bài học không tồn tại hoặc đang bị ẩn."));
        int count=(int)outline.stream().filter(l->completed.contains(l.getId())).count();
        Set<Long> passed=new HashSet<>(em.createQuery("select distinct a.quiz.id from QuizAttempt a where a.student.id=:student and a.quiz.course.id=:course and a.passed=true and a.submittedAt is not null",Long.class)
            .setParameter("student",student.getId()).setParameter("course",courseId).getResultList());
        List<LessonQuiz> assessmentRows=new ArrayList<>();
        for(var item:lessonAssessments.describe(courseId)) assessmentRows.add(new LessonQuiz(item.id(),item.lessonId(),item.title(),item.durationMinutes(),item.passRate(),item.questionCount(),item.id()!=null && passed.contains(item.id())));
        int passedCount=(int)assessmentRows.stream().filter(LessonQuiz::passed).count();
        int total=outline.size()+assessmentRows.size();
        int percent=total==0?0:(count+passedCount)*100/total;
        return new LearningCourse(courseId,course.getTitle(),course.getExpert().getFullName(),count,percent,
            outline.stream().map(l->new LessonItem(l.getId(),l.getTitle(),l.getPosition(),completed.contains(l.getId()))).toList(),
            selected==null?null:new LessonContent(selected.getId(),selected.getTitle(),selected.getContent(),selected.getVideo()==null?null:selected.getVideo().getId()),
            List.copyOf(assessmentRows));
    }
    @Transactional
    public LearningCourse complete(Long courseId,Long lessonId,String email) {
        User student=student(email,true);accessible(courseId,student);
        Lesson lesson=lessons.findByIdAndCourseId(lessonId,courseId).filter(Lesson::isActive).orElseThrow(()->new ApiException(HttpStatus.NOT_FOUND,"Không tìm thấy bài học đang hoạt động."));
        var record=progress.findByStudentIdAndLessonId(student.getId(),lessonId).orElseGet(()->{var p=new StudentLessonProgress();p.setStudent(student);p.setLesson(lesson);return p;});
        if(!record.isCompleted()){record.setRegistration(registrations.findAccessRegistration(student.getId(),courseId,Instant.now(),org.springframework.data.domain.PageRequest.of(0,1)).stream().findFirst().orElseThrow(()->new ApiException(HttpStatus.FORBIDDEN,"Quyền học đã hết hạn.")));record.setStatus("COMPLETED");record.setCompleted(true);record.setProgressPercent(100);record.setCompletedAt(Instant.now());progress.saveAndFlush(record);}
        certificateService.tryIssue(student.getId(),courseId);
        return read(courseId,lessonId,email);
    }
    public record PreparedQuestions(Long quizId) {}
    @Transactional public PreparedQuestions prepareQuestions(Long courseId,Long lessonId,String email) {
        User student=student(email,true);
        Course course=courses.findForUpdate(courseId).orElseThrow(()->new ApiException(HttpStatus.NOT_FOUND,"Khóa học không tồn tại."));
        accessible(courseId,student);
        return new PreparedQuestions(lessonAssessments.prepare(course,lessonId));
    }
    private Course accessible(Long id,User student) {
        Course course=courses.findById(id).filter(c->c.getStatus()==CourseStatus.PUBLISHED).orElseThrow(()->new ApiException(HttpStatus.NOT_FOUND,"Khóa học không còn khả dụng."));
        if(registrations.countCurrentAccess(student.getId(),id,Instant.now())==0) throw new ApiException(HttpStatus.FORBIDDEN,"Bạn chưa có quyền học hoặc đăng ký đã hết hạn.");
        return course;
    }
    private User student(String email,boolean lock) {
        User student=users.findByEmailIgnoreCase(email).orElseThrow(()->new ApiException(HttpStatus.UNAUTHORIZED,"Không tìm thấy tài khoản."));
        if(lock) em.lock(student,jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);
        if(!student.isEnabled()||student.isBlocked()||student.getRoles().stream().noneMatch(r->"STUDENT".equals(r.getCode()))) throw new ApiException(HttpStatus.FORBIDDEN,"Chỉ Student đang hoạt động được học.");
        return student;
    }
}

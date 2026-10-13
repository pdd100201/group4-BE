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
    private final UserRepository users;
    private final CourseRepository courses;
    private final LessonRepository lessons;
    private final CourseRegistrationRepository registrations;
    private final StudentLessonProgressRepository progress;
    private final jakarta.persistence.EntityManager em;
    private final QuizRepository quizzes;
    public record LessonItem(Long id,String title,Integer position,boolean completed) {}
    public record LessonContent(Long id,String title,String content,Long videoMediaId) {}
    public record LessonQuiz(Long id,Long lessonId,String title,Integer durationMinutes,Integer passRate,int questionCount) {}
    public record LearningCourse(Long courseId,String courseTitle,String expertName,int completedLessons,int progressPercent,
        List<LessonItem> lessons,LessonContent currentLesson,List<LessonQuiz> quizzes) {}

    public LearningCourse read(Long courseId,Long lessonId,String email) {
        User student=student(email,false);Course course=accessible(courseId,student);
        var outline=lessons.findByCourseIdOrderByPositionAscIdAsc(courseId).stream().filter(Lesson::isActive).toList();
        Set<Long> completed=new HashSet<>();progress.findByStudentIdAndLessonCourseId(student.getId(),courseId).stream().filter(StudentLessonProgress::isCompleted).forEach(p->completed.add(p.getLesson().getId()));
        Lesson selected=lessonId==null ? outline.stream().filter(l->!completed.contains(l.getId())).findFirst().orElse(outline.isEmpty()?null:outline.getFirst()) : outline.stream().filter(l->l.getId().equals(lessonId)).findFirst().orElseThrow(()->new ApiException(HttpStatus.NOT_FOUND,"Bài học không tồn tại hoặc đang bị ẩn."));
        int count=(int)outline.stream().filter(l->completed.contains(l.getId())).count();
        return new LearningCourse(courseId,course.getTitle(),course.getExpert().getFullName(),count,outline.isEmpty()?0:count*100/outline.size(),
            outline.stream().map(l->new LessonItem(l.getId(),l.getTitle(),l.getPosition(),completed.contains(l.getId()))).toList(),
            selected==null?null:new LessonContent(selected.getId(),selected.getTitle(),selected.getContent(),selected.getVideo()==null?null:selected.getVideo().getId()),
            quizzes.findByCourseIdOrderByIdAsc(courseId).stream().filter(Quiz::isActive)
                .filter(q->q.getLesson()==null || q.getLesson().isActive())
                .filter(q->!q.getQuizQuestions().isEmpty()).map(q->new LessonQuiz(q.getId(),q.getLesson()==null?null:q.getLesson().getId(),q.getTitle(),q.getDurationMinutes(),q.getPassRate(),q.getQuizQuestions().size())).toList());
    }
    @Transactional
    public LearningCourse complete(Long courseId,Long lessonId,String email) {
        User student=student(email,true);accessible(courseId,student);
        Lesson lesson=lessons.findByIdAndCourseId(lessonId,courseId).filter(Lesson::isActive).orElseThrow(()->new ApiException(HttpStatus.NOT_FOUND,"Không tìm thấy bài học đang hoạt động."));
        var record=progress.findByStudentIdAndLessonId(student.getId(),lessonId).orElseGet(()->{var p=new StudentLessonProgress();p.setStudent(student);p.setLesson(lesson);return p;});
        if(!record.isCompleted()){record.setRegistration(registrations.findAccessRegistration(student.getId(),courseId,Instant.now(),org.springframework.data.domain.PageRequest.of(0,1)).stream().findFirst().orElseThrow(()->new ApiException(HttpStatus.FORBIDDEN,"Quyền học đã hết hạn.")));record.setStatus("COMPLETED");record.setCompleted(true);record.setProgressPercent(100);record.setCompletedAt(Instant.now());progress.saveAndFlush(record);}
        return read(courseId,lessonId,email);
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

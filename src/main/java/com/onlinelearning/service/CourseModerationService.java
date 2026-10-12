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

/** UC27/UC28, BR26/BR27: moderation without editing another Expert's content. */
@Service @RequiredArgsConstructor @Transactional
public class CourseModerationService {
    private final CourseRepository courses;
    private final UserRepository users;
    private final AuditLogRepository logs;
    private final EntityManager em;

    public Course archive(Long id, String email, String reason) {
        User actor = moderator(email);
        String explanation = reason(reason);
        Course course = locked(id);
        if (course.getStatus() != CourseStatus.PUBLISHED)
            throw new ApiException(HttpStatus.CONFLICT, "Chỉ khóa học Published mới được ẩn.");
        course.setStatus(CourseStatus.ARCHIVED);
        audit(course, actor, "COURSE_ARCHIVED", explanation);
        return course;
    }

    public void delete(Long id, String email, String reason) {
        User actor = moderator(email);
        String explanation = reason(reason);
        Course course = locked(id);
        if (course.getStatus() == CourseStatus.PENDING_REVIEW || course.getStatus() == CourseStatus.APPROVED)
            throw new ApiException(HttpStatus.CONFLICT, "Không thể xóa khóa học đang chờ duyệt hoặc ở trạng thái legacy.");
        // Registrations retain payment/transaction/report sources; quiz attempts and
        // learning progress may also exist independently of a registration.
        for (String query : new String[]{
            "select count(r) from CourseRegistration r where r.course.id=:id",
            "select count(a) from QuizAttempt a where a.quiz.course.id=:id",
            "select count(p) from StudentLessonProgress p where p.lesson.course.id=:id",
            "select count(a) from QuizAnswer a where a.question.course.id=:id",
            "select count(o) from QuizAnswerOption o where o.questionOption.question.course.id=:id",
            "select count(l) from QuizQuestion l where l.question.course.id=:id and l.quiz.course.id<>:id"}) {
            if (em.createQuery(query, Long.class).setParameter("id", id).getSingleResult() > 0)
                throw new ApiException(HttpStatus.CONFLICT,
                    "Khóa học có dữ liệu cần lưu giữ, không thể xóa hẳn. Hãy ẩn khóa học để giữ lịch sử.");
        }
        audit(course, actor, "COURSE_DELETED", explanation);
        em.flush();
        // Delete learning content only after checking all retained references.
        em.createQuery("delete from QuizQuestion l where l.quiz.id in (select q.id from Quiz q where q.course.id=:id)").setParameter("id", id).executeUpdate();
        em.createQuery("delete from QuestionOption o where o.question.id in (select q.id from Question q where q.course.id=:id)").setParameter("id", id).executeUpdate();
        em.createQuery("delete from Quiz q where q.course.id=:id").setParameter("id", id).executeUpdate();
        em.createQuery("delete from Question q where q.course.id=:id").setParameter("id", id).executeUpdate();
        em.createQuery("delete from Lesson l where l.course.id=:id").setParameter("id", id).executeUpdate();
        // Cloudinary assets are retained; only the deleted course association is cleared.
        em.createQuery("update MediaAsset a set a.course=null where a.course.id=:id").setParameter("id", id).executeUpdate();
        // Bulk mutations bypass the persistence context. Clear stale child objects
        // before deleting the parent so a flush cannot reintroduce their references.
        em.clear();
        em.createQuery("delete from Course c where c.id=:id").setParameter("id", id).executeUpdate();
        em.flush();
    }

    private Course locked(Long id) {
        return courses.findForUpdate(id).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Không tìm thấy khóa học."));
    }
    private User moderator(String email) {
        User actor = users.findByEmailIgnoreCase(email).orElseThrow(() -> new ApiException(HttpStatus.FORBIDDEN, "Tài khoản không hợp lệ."));
        if (actor.getRoles().stream().noneMatch(r -> "ADMIN".equals(r.getCode()) || "MANAGER".equals(r.getCode())))
            throw new ApiException(HttpStatus.FORBIDDEN, "Chỉ Admin/Manager được thực hiện thao tác này.");
        return actor;
    }
    private String reason(String value) {
        if (value == null || value.isBlank() || value.length() > 255)
            throw new ApiException(HttpStatus.BAD_REQUEST, "Lý do phải có từ 1 đến 255 ký tự.");
        return value.trim();
    }
    private void audit(Course course, User actor, String action, String reason) {
        AuditLog log = new AuditLog(); log.setActor(actor); log.setAction(action);
        log.setTargetType("COURSE"); log.setTargetId(course.getId().toString());
        log.setMetadata(reason); log.setOccurredAt(Instant.now()); logs.save(log);
    }
}

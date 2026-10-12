package com.onlinelearning.service;

import com.onlinelearning.entity.*;
import com.onlinelearning.exception.ApiException;
import com.onlinelearning.repository.CourseRepository;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.ArrayList;
import java.util.List;

/** Shared read/write validation for UC66/UC69; no minimum five lessons. */
@Service @RequiredArgsConstructor @Transactional(readOnly=true)
public class CourseSubmissionReadinessService {
    private final CourseRepository courses;
    private final EntityManager em;
    public record Check(String code, String label, boolean complete) {}
    public record Readiness(boolean eligible, boolean ready, List<Check> checks) {}

    public Readiness get(Long id, String email) {
        Course course = courses.findById(id).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Không tìm thấy khóa học."));
        if (!course.getExpert().getEmail().equalsIgnoreCase(email))
            throw new ApiException(HttpStatus.FORBIDDEN, "Bạn chỉ được gửi duyệt khóa học của mình.");
        return inspect(course);
    }

    public Readiness inspect(Course course) {
        List<Check> checks = new ArrayList<>();
        checks.add(new Check("BASIC", "Có tên, danh mục và mô tả khóa học", text(course.getTitle()) && text(course.getCategory()) && text(course.getDescription())));
        checks.add(new Check("THUMBNAIL", "Có ảnh khóa học", course.getThumbnail() != null));
        checks.add(new Check("PRICE", "Giá khóa học hợp lệ (không âm)", course.getPrice() != null && course.getPrice().signum() >= 0));
        var lessons = em.createQuery("select l from Lesson l left join fetch l.video where l.course.id=:id order by l.id", Lesson.class)
            .setParameter("id", course.getId()).getResultList();
        checks.add(new Check("LESSONS", "Có ít nhất một bài học; tất cả bài học Active, đủ tên/nội dung/thứ tự và video", !lessons.isEmpty() && lessons.stream().allMatch(l ->
            l.isActive() && text(l.getTitle()) && text(l.getContent()) && l.getPosition() != null && l.getPosition() > 0
            && l.getVideo() != null && "video".equals(l.getVideo().getResourceType()))));
        var quizzes = em.createQuery("select q from Quiz q where q.course.id=:id", Quiz.class)
            .setParameter("id", course.getId()).getResultList();
        checks.add(new Check("QUIZZES", "Có ít nhất một quiz; tất cả quiz Active và đã cấu hình thời lượng, tỷ lệ đạt, số lần thử", !quizzes.isEmpty() && quizzes.stream().allMatch(q ->
            q.isActive() && text(q.getTitle()) && q.getDurationMinutes() != null && q.getDurationMinutes() > 0
            && q.getPassRate() != null && q.getPassRate() >= 0 && q.getPassRate() <= 100
            && q.getMaxAttempts() != null && q.getMaxAttempts() > 0)));
        boolean eligible = course.getStatus() == CourseStatus.DRAFT || course.getStatus() == CourseStatus.REJECTED;
        return new Readiness(eligible, eligible && checks.stream().allMatch(Check::complete), List.copyOf(checks));
    }

    public void requireReady(Course course) {
        var result = inspect(course);
        if (!result.ready()) throw new ApiException(HttpStatus.BAD_REQUEST,
            result.checks().stream().filter(check -> !check.complete()).map(Check::label).findFirst().orElse("Khóa học không thể gửi duyệt."));
    }
    private boolean text(String value) { return value != null && !value.isBlank(); }
}

package com.onlinelearning.service;
import com.onlinelearning.dto.course.*;
import com.onlinelearning.entity.*;
import com.onlinelearning.exception.ApiException;
import com.onlinelearning.repository.*;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.util.List;

@Service @RequiredArgsConstructor @Transactional
public class ExpertLessonService {
    private final CourseRepository courses;
    private final LessonRepository lessons;
    private final AuditLogRepository audit;
    private final EntityManager em;
    private final MediaService media;
    @Transactional(readOnly=true)
    public List<LessonResponse> list(Long courseId, String email) {
        owned(courseId, email, false);
        return lessons.findByCourseIdOrderByPositionAscIdAsc(courseId).stream().map(LessonResponse::from).toList();
    }
    @Transactional(readOnly=true)
    public LessonResponse get(Long courseId, Long id, String email) {
        owned(courseId, email, false); return LessonResponse.from(lesson(courseId, id));
    }
    public LessonResponse create(Long courseId, String email, LessonRequest request) {
        Course c = owned(courseId, email, true); editable(c);
        position(courseId, request.position(), -1L);
        Lesson l = new Lesson(); l.setCourse(c); apply(l, request); l.setVideo(media.attachment(request.videoMediaId(), email, courseId, "LESSON_VIDEO")); lessons.save(l); record(l, "LESSON_CREATED"); return LessonResponse.from(l);
    }
    public LessonResponse update(Long courseId, Long id, String email, LessonRequest request) {
        Course c = owned(courseId, email, true); editable(c);
        Lesson l = lesson(courseId, id); position(courseId, request.position(), id);
        apply(l, request); l.setVideo(media.attachment(request.videoMediaId(), email, courseId, "LESSON_VIDEO")); record(l, "LESSON_UPDATED"); return LessonResponse.from(l);
    }
    public void delete(Long courseId, Long id, String email) {
        Course c = owned(courseId, email, true); editable(c); Lesson l = lesson(courseId, id);
        Long retained = em.createQuery("select count(p) from StudentLessonProgress p where p.lesson.id = :id", Long.class).setParameter("id", id).getSingleResult();
        if (retained > 0) throw new ApiException(HttpStatus.CONFLICT, "Không thể xóa bài học đã có tiến độ học tập của Student.");
        record(l, "LESSON_DELETED"); lessons.delete(l); lessons.flush();
    }
    private Course owned(Long id, String email, boolean lock) {
        Course c = (lock ? courses.findForUpdate(id) : courses.findById(id)).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Không tìm thấy khóa học."));
        if (!c.getExpert().getEmail().equalsIgnoreCase(email)) throw new ApiException(HttpStatus.FORBIDDEN, "Bạn chỉ được quản lý bài học trong khóa học của mình.");
        return c;
    }
    private void editable(Course c) {
        if (c.getStatus() != CourseStatus.DRAFT && c.getStatus() != CourseStatus.REJECTED) throw new ApiException(HttpStatus.CONFLICT, "Chỉ được thay đổi bài học khi khóa học là Nháp hoặc Bị từ chối.");
    }
    private Lesson lesson(Long courseId, Long id) { return lessons.findByIdAndCourseId(id, courseId).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Bài học không thuộc khóa học này hoặc không tồn tại.")); }
    private void position(Long courseId, Integer position, Long id) {
        if (lessons.existsByCourseIdAndPositionAndIdNot(courseId, position, id)) throw new ApiException(HttpStatus.CONFLICT, "Thứ tự này đã có bài học. Hãy chọn thứ tự khác.");
    }
    private void apply(Lesson l, LessonRequest r) { l.setTitle(r.title().trim()); l.setContent(r.content().trim()); l.setPosition(r.position()); l.setActive(r.active()); }
    private void record(Lesson l, String action) {
        AuditLog log = new AuditLog(); log.setActor(l.getCourse().getExpert()); log.setAction(action); log.setTargetType("LESSON"); log.setTargetId(l.getId().toString());
        log.setMetadata("courseId=" + l.getCourse().getId()); log.setOccurredAt(Instant.now()); audit.save(log);
    }
}

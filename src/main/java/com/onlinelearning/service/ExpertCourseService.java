package com.onlinelearning.service;

import com.onlinelearning.dto.course.*;
import com.onlinelearning.entity.*;
import com.onlinelearning.exception.ApiException;
import com.onlinelearning.repository.*;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;

@Service @RequiredArgsConstructor @Transactional
public class ExpertCourseService {
    private final CourseRepository courses;
    private final UserRepository users;
    private final AuditLogRepository auditLogs;
    private final EntityManager entityManager;
    private final MediaService media;

    @Transactional(readOnly=true)
    public Page<ExpertCourseResponse> list(String email, String search, String category, CourseStatus status, int page, int size) {
        User actor = user(email);
        Specification<Course> spec = (root, query, cb) -> cb.equal(root.get("expert").get("id"), actor.getId());
        if (search != null && !search.isBlank()) {
            String pattern = "%" + search.trim().toLowerCase().replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
            spec = spec.and((root, query, cb) -> cb.like(cb.lower(root.get("title")), pattern, '\\'));
        }
        if (category != null && !category.isBlank()) spec = spec.and((root, query, cb) -> cb.equal(root.get("category"), category.trim()));
        if (status != null) spec = spec.and((root, query, cb) -> cb.equal(root.get("status"), status));
        return courses.findAll(spec, PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "id"))).map(ExpertCourseResponse::from);
    }

    @Transactional(readOnly=true)
    public ExpertCourseResponse get(Long id, String email) { return ExpertCourseResponse.from(owned(id, email, false)); }

    public ExpertCourseResponse create(String email, CourseRequest request) {
        Course c = new Course();
        c.setExpert(user(email));
        apply(c, request);
        courses.save(c);
        if (request.thumbnailMediaId() != null) c.setThumbnail(media.attachment(request.thumbnailMediaId(), email, c.getId(), "COURSE_IMAGE"));
        record(c, "COURSE_CREATED");
        return ExpertCourseResponse.from(c);
    }

    public ExpertCourseResponse update(Long id, String email, CourseRequest request) {
        Course c = owned(id, email, true);
        editable(c);
        apply(c, request);
        record(c, "COURSE_UPDATED");
        c.setThumbnail(media.attachment(request.thumbnailMediaId(), email, id, "COURSE_IMAGE"));
        return ExpertCourseResponse.from(c);
    }

    public void delete(Long id, String email) {
        Course c = owned(id, email, true);
        if (c.getStatus() != CourseStatus.DRAFT && c.getStatus() != CourseStatus.REJECTED)
            throw new ApiException(HttpStatus.CONFLICT, "Expert chỉ được xóa khóa học Nháp hoặc Bị từ chối.");
        for (String entity : new String[]{"CourseRegistration", "Lesson", "Question", "Quiz"}) {
            Long count = entityManager.createQuery("select count(e) from " + entity + " e where e.course.id = :id", Long.class)
                .setParameter("id", id).getSingleResult();
            if (count > 0) throw new ApiException(HttpStatus.CONFLICT, "Không thể xóa khóa học còn nội dung hoặc dữ liệu đăng ký liên quan.");
        }
        record(c, "COURSE_DELETED");
        entityManager.createQuery("select a from MediaAsset a where a.course.id=:id", MediaAsset.class).setParameter("id", id).getResultList().forEach(a -> a.setCourse(null));
        courses.delete(c);
        courses.flush();
    }

    private Course owned(Long id, String email, boolean lock) {
        Course c = (lock ? courses.findForUpdate(id) : courses.findById(id))
            .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Không tìm thấy khóa học."));
        if (!c.getExpert().getEmail().equalsIgnoreCase(email)) throw new ApiException(HttpStatus.FORBIDDEN, "Bạn chỉ được quản lý khóa học của mình.");
        return c;
    }
    private User user(String email) {
        return users.findByEmailIgnoreCase(email).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Không tìm thấy tài khoản."));
    }
    private void editable(Course c) {
        if (c.getStatus() != CourseStatus.DRAFT && c.getStatus() != CourseStatus.REJECTED && c.getStatus() != CourseStatus.PUBLISHED)
            throw new ApiException(HttpStatus.CONFLICT, "Chỉ được sửa khóa học Nháp, Bị từ chối hoặc Đã xuất bản.");
    }
    private void apply(Course c, CourseRequest r) {
        c.setTitle(r.title().trim()); c.setDescription(r.description().trim());
        c.setCategory(r.category().trim()); c.setPrice(r.price());
    }
    private void record(Course c, String action) {
        AuditLog log = new AuditLog(); log.setActor(c.getExpert()); log.setAction(action);
        log.setTargetType("COURSE"); log.setTargetId(c.getId().toString()); log.setOccurredAt(Instant.now()); auditLogs.save(log);
    }
}

package com.onlinelearning.service;

import com.onlinelearning.entity.*;
import com.onlinelearning.exception.ApiException;
import com.onlinelearning.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** UC27: restoration is moderation, not permission to approve or edit content. */
@Service
@RequiredArgsConstructor
public class CourseRestorationService {
    private final CourseRepository courses;
    private final UserRepository users;
    private final AuditLogRepository auditLogs;

    @Transactional
    public Course restore(Long id, String email, String reason) {
        User actor = users.findByEmailIgnoreCase(email)
            .orElseThrow(() -> new ApiException(HttpStatus.FORBIDDEN, "Tài khoản không hợp lệ."));
        if (actor.getRoles().stream().noneMatch(r -> "ADMIN".equals(r.getCode()) || "MANAGER".equals(r.getCode())))
            throw new ApiException(HttpStatus.FORBIDDEN, "Chỉ Admin/Manager được khôi phục khóa học.");
        if (reason == null || reason.isBlank() || reason.length() > 255)
            throw new ApiException(HttpStatus.BAD_REQUEST, "Lý do khôi phục phải có từ 1 đến 255 ký tự.");
        Course course = courses.findForUpdate(id)
            .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Không tìm thấy khóa học."));
        if (course.getStatus() != CourseStatus.ARCHIVED)
            throw new ApiException(HttpStatus.CONFLICT, "Chỉ khóa học ARCHIVED mới được khôi phục.");
        course.setStatus(CourseStatus.PUBLISHED);
        AuditLog log = new AuditLog();
        log.setActor(actor);
        log.setAction("COURSE_RESTORED");
        log.setTargetType("COURSE");
        log.setTargetId(id.toString());
        log.setMetadata(reason.trim());
        log.setOccurredAt(java.time.Instant.now());
        auditLogs.save(log);
        return course;
    }
}

package com.onlinelearning.dto.course;

import com.onlinelearning.entity.*;
import java.math.BigDecimal;

public record ExpertCourseResponse(Long id, String title, String description, String category,
        BigDecimal price, CourseStatus status, String rejectionReason, boolean editable, boolean deletable, Long thumbnailMediaId, String thumbnailUrl) {
    public static ExpertCourseResponse from(Course c) {
        return new ExpertCourseResponse(c.getId(), c.getTitle(), c.getDescription(), c.getCategory(),
            c.getPrice(), c.getStatus(), c.getRejectionReason(),
            c.getStatus() == CourseStatus.DRAFT || c.getStatus() == CourseStatus.REJECTED || c.getStatus() == CourseStatus.PUBLISHED,
            c.getStatus() == CourseStatus.DRAFT || c.getStatus() == CourseStatus.REJECTED,
            c.getThumbnail() == null ? null : c.getThumbnail().getId(), c.getThumbnail() == null ? null : c.getThumbnail().getSecureUrl());
    }
}

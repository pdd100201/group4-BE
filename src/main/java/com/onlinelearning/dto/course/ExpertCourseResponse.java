package com.onlinelearning.dto.course;

import com.onlinelearning.entity.*;
import java.math.BigDecimal;

public record ExpertCourseResponse(Long id, String title, String description, String category,
        BigDecimal price, CourseStatus status, String rejectionReason, boolean editable, boolean deletable, Long thumbnailMediaId, String thumbnailUrl, BigDecimal displayPrice, boolean requiresDeletionApproval, String deletionRequestStatus, String deletionRequestReason, java.time.Instant deletionRequestedAt, String deletionReviewReason, java.time.Instant deletionReviewedAt) {
    public static ExpertCourseResponse from(Course c) {
        return from(c, null);
    }
    public static ExpertCourseResponse from(Course c, BigDecimal displayPrice) {
        return new ExpertCourseResponse(c.getId(), c.getTitle(), c.getDescription(), c.getCategory(),
            c.getPrice(), c.getStatus(), c.getRejectionReason(),
            c.getDeletedAt() == null && !c.isDeletionPending() && (c.getStatus() == CourseStatus.DRAFT || c.getStatus() == CourseStatus.REJECTED || c.getStatus() == CourseStatus.PUBLISHED),
            c.getDeletedAt() == null && !c.isDeletionPending(),
            c.getThumbnail() == null ? null : c.getThumbnail().getId(), c.getThumbnail() == null ? null : c.getThumbnail().getSecureUrl(), displayPrice, c.requiresDeletionApproval(), c.getDeletionRequestStatus(), c.getDeletionRequestReason(), c.getDeletionRequestedAt(), c.getDeletionReviewReason(), c.getDeletionReviewedAt());
    }
}

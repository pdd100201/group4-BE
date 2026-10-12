package com.onlinelearning.dto.course;

import java.math.BigDecimal;
import java.util.List;

/** UC11: public metadata only; no lesson content, media IDs or quiz answers. */
public record PublicCourseDetailResponse(Long id, String title, String description, String category,
        BigDecimal price, String thumbnailUrl, String expertName, List<LessonOutline> lessons, long quizCount,
        List<CoursePackageResponse> packages) {
    public record LessonOutline(Long id, String title, Integer position) {}
}

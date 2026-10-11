package com.onlinelearning.dto.course;

import com.onlinelearning.entity.Course;
import java.math.BigDecimal;

public record CourseSummaryResponse(Long id, String title, String description, String category, BigDecimal price, String thumbnailUrl, String expertName) {
    public static CourseSummaryResponse from(Course course) {
        return new CourseSummaryResponse(course.getId(), course.getTitle(), course.getDescription(), course.getCategory(), course.getPrice(), course.getThumbnail() == null ? null : course.getThumbnail().getSecureUrl(), course.getExpert().getFullName());
    }
}

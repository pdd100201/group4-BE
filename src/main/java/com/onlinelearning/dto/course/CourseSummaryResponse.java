package com.onlinelearning.dto.course;

import com.onlinelearning.entity.Course;
import java.math.BigDecimal;

public record CourseSummaryResponse(Long id, String title, String description, String category, BigDecimal price) {
    public static CourseSummaryResponse from(Course course) {
        return new CourseSummaryResponse(course.getId(), course.getTitle(), course.getDescription(), course.getCategory(), course.getPrice());
    }
}

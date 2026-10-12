package com.onlinelearning.dto.course;

import java.util.List;
import org.springframework.data.domain.Page;

public record StudentCoursePage(List<StudentCourseResponse> content, int page, int size, long totalElements, int totalPages) {
    public static StudentCoursePage from(Page<StudentCourseResponse> page) {
        return new StudentCoursePage(page.getContent(), page.getNumber(), page.getSize(), page.getTotalElements(), page.getTotalPages());
    }
}

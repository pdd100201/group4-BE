package com.onlinelearning.dto.course;

import com.onlinelearning.entity.Course;
import com.onlinelearning.entity.CourseStatus;

public record CourseWorkflowResponse(Long id, String title, CourseStatus status, String rejectionReason) {
    public static CourseWorkflowResponse from(Course course) {
        return new CourseWorkflowResponse(course.getId(), course.getTitle(), course.getStatus(), course.getRejectionReason());
    }
}

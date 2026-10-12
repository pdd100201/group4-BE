package com.onlinelearning.dto.course;

import com.onlinelearning.entity.CourseRegistration;
import com.onlinelearning.entity.CourseStatus;
import java.time.Instant;

public record StudentCourseResponse(Long registrationId, Long courseId, String title, String category,
        String expertName, String thumbnailUrl, String packageName, Instant validFrom, Instant validUntil,
        boolean lifetime, boolean available) {
    public static StudentCourseResponse from(CourseRegistration r) {
        var course = r.getCourse();
        return new StudentCourseResponse(r.getId(), course.getId(), course.getTitle(), course.getCategory(),
                course.getExpert().getFullName(), course.getThumbnail() == null ? null : course.getThumbnail().getSecureUrl(),
                r.getPackageNameSnapshot(), r.getValidFrom(), r.getValidUntil(), Boolean.TRUE.equals(r.getLifetimeSnapshot()),
                course.getStatus() == CourseStatus.PUBLISHED);
    }
}

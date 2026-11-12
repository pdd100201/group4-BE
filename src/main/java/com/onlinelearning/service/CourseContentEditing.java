package com.onlinelearning.service;

import com.onlinelearning.entity.Course;
import com.onlinelearning.entity.CourseStatus;
import com.onlinelearning.exception.ApiException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CourseContentEditing {
    private final CourseReviewChangesService changes;

    // Call inside the mutation's transaction, after locking and checking ownership.
    public void begin(Course course) {
        if (course.getStatus()!=CourseStatus.DRAFT && course.getStatus()!=CourseStatus.REJECTED && course.getStatus()!=CourseStatus.PUBLISHED)
            throw new ApiException(HttpStatus.CONFLICT,"Khóa học đang chờ duyệt hoặc đã bị ẩn, không thể sửa nội dung.");
        changes.capturePublished(course);
        course.requireReviewAfterEdit();
    }
}

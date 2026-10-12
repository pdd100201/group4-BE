package com.onlinelearning.service;

import com.onlinelearning.dto.course.StudentCourseResponse;
import org.springframework.data.domain.Page;

public interface StudentCourseService {
    Page<StudentCourseResponse> list(String email, String search, String sort, int page, int size);
}

package com.onlinelearning.controller;

import com.onlinelearning.dto.course.CourseSummaryResponse;
import com.onlinelearning.service.CourseCatalogService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PathVariable;

@RestController
@RequestMapping("/api/v1/public/courses")
@RequiredArgsConstructor
public class PublicCourseController {
    private final CourseCatalogService courses;

    @GetMapping
    public List<CourseSummaryResponse> list() {
        return courses.listPublished();
    }

    @GetMapping("/{id}")
    public com.onlinelearning.dto.course.PublicCourseDetailResponse detail(@PathVariable Long id) {
        return courses.getPublished(id);
    }

    @org.springframework.web.bind.annotation.ExceptionHandler(org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class)
    @org.springframework.web.bind.annotation.ResponseStatus(org.springframework.http.HttpStatus.BAD_REQUEST)
    public java.util.Map<String, String> invalidId() { return java.util.Map.of("message", "Mã khóa học không hợp lệ."); }
}

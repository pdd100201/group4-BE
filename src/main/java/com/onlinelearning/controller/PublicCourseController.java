package com.onlinelearning.controller;

import com.onlinelearning.dto.course.CourseSummaryResponse;
import com.onlinelearning.service.CourseCatalogService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/public/courses")
@RequiredArgsConstructor
public class PublicCourseController {
    private final CourseCatalogService courses;

    @GetMapping
    public List<CourseSummaryResponse> list() {
        return courses.listPublished();
    }
}

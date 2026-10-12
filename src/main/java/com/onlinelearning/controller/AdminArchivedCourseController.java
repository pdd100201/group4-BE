package com.onlinelearning.controller;

import com.onlinelearning.entity.CourseStatus;
import com.onlinelearning.service.ManagerCourseService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/** Metadata-only list; does not grant Admin access to quiz answers or review actions. */
@RestController
@RequiredArgsConstructor
public class AdminArchivedCourseController {
    private final ManagerCourseService courses;

    @GetMapping("/api/v1/admin/courses")
    public ManagerCourseController.CoursePage published(@RequestParam(defaultValue = "0") int page) {
        if (page < 0) throw new com.onlinelearning.exception.ApiException(
            org.springframework.http.HttpStatus.BAD_REQUEST, "Phân trang không hợp lệ.");
        var result = courses.list("", "", CourseStatus.PUBLISHED, page, 10);
        return new ManagerCourseController.CoursePage(result.getContent(), result.getTotalElements(), result.getTotalPages(), result.getNumber());
    }

    @GetMapping("/api/v1/admin/courses/archived")
    public ManagerCourseController.CoursePage list(@RequestParam(defaultValue = "0") int page) {
        if (page < 0) throw new com.onlinelearning.exception.ApiException(
            org.springframework.http.HttpStatus.BAD_REQUEST, "Phân trang không hợp lệ.");
        var result = courses.list("", "", CourseStatus.ARCHIVED, page, 10);
        return new ManagerCourseController.CoursePage(result.getContent(), result.getTotalElements(), result.getTotalPages(), result.getNumber());
    }
}

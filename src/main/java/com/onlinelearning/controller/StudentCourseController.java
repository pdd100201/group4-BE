package com.onlinelearning.controller;

import com.onlinelearning.dto.course.StudentCoursePage;
import com.onlinelearning.service.StudentCourseService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController @RequiredArgsConstructor @RequestMapping("/api/v1/student/courses")
public class StudentCourseController {
    private final StudentCourseService courses;
    @GetMapping public StudentCoursePage list(Authentication actor, @RequestParam(defaultValue = "") String q,
            @RequestParam(defaultValue = "newest") String sort, @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "12") int size) {
        return StudentCoursePage.from(courses.list(actor.getName(), q, sort, page, size));
    }
}

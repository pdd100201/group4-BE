package com.onlinelearning.controller;

import com.onlinelearning.service.CourseReviewChangesService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController @RequiredArgsConstructor
@RequestMapping("/api/v1/manager/courses")
public class CourseReviewChangesController {
    private final CourseReviewChangesService changes;
    @GetMapping("/{id}/changes")
    public CourseReviewChangesService.Comparison changes(@PathVariable Long id) { return changes.compare(id); }
}

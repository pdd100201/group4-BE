package com.onlinelearning.controller;

import com.onlinelearning.service.CourseSubmissionReadinessService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController @RequiredArgsConstructor
public class CourseSubmissionReadinessController {
    private final CourseSubmissionReadinessService readiness;
    @GetMapping("/api/v1/expert/courses/{id}/submission-readiness")
    public CourseSubmissionReadinessService.Readiness get(@PathVariable Long id, Authentication actor) {
        return readiness.get(id, actor.getName());
    }
}

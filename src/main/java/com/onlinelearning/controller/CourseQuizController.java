package com.onlinelearning.controller;

import com.onlinelearning.dto.quiz.CourseQuizRequest;
import com.onlinelearning.service.CourseQuizService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController @RequiredArgsConstructor
@RequestMapping("/api/v1/expert/courses/{courseId}/assessment")
public class CourseQuizController {
    private final CourseQuizService service;
    @GetMapping public List<CourseQuizService.Assessment> list(@PathVariable Long courseId, Authentication actor) {
        return service.list(courseId, actor.getName());
    }
    @PutMapping public CourseQuizService.Assessment save(@PathVariable Long courseId,
            @Valid @RequestBody CourseQuizRequest input, Authentication actor) {
        return service.save(courseId, actor.getName(), input);
    }
}

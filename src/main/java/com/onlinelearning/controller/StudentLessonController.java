package com.onlinelearning.controller;

import com.onlinelearning.service.StudentLessonService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController @RequiredArgsConstructor @RequestMapping("/api/v1/student/courses/{courseId}")
public class StudentLessonController {
    private final StudentLessonService lessons;
    @GetMapping("/learning") public StudentLessonService.LearningCourse course(@PathVariable Long courseId,Authentication actor) { return lessons.read(courseId,null,actor.getName()); }
    @GetMapping("/lessons/{id}") public StudentLessonService.LearningCourse lesson(@PathVariable Long courseId,@PathVariable Long id,Authentication actor) { return lessons.read(courseId,id,actor.getName()); }
    @PostMapping("/lessons/{id}/complete") public StudentLessonService.LearningCourse complete(@PathVariable Long courseId,@PathVariable Long id,Authentication actor) { return lessons.complete(courseId,id,actor.getName()); }
}

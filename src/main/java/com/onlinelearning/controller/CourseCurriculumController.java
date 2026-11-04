package com.onlinelearning.controller;
import com.onlinelearning.service.CourseCurriculumService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController @RequiredArgsConstructor @RequestMapping("/api/v1/expert/courses/{courseId}/curriculum")
public class CourseCurriculumController {
 private final CourseCurriculumService curriculum;
 public record OrderRequest(@NotNull @Size(max=10000) List<@NotNull @Positive Long> lessonIds,@NotNull @Size(max=10000) List<@NotNull @Positive Long> expectedLessonIds) {}
 @GetMapping public CourseCurriculumService.Outline read(@PathVariable Long courseId,Authentication actor){return curriculum.read(courseId,actor.getName());}
 @PutMapping("/order") public CourseCurriculumService.Outline reorder(@PathVariable Long courseId,Authentication actor,@Valid @RequestBody OrderRequest request){return curriculum.reorder(courseId,actor.getName(),request.lessonIds(),request.expectedLessonIds());}
}

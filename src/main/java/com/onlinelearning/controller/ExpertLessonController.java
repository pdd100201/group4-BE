package com.onlinelearning.controller;
import com.onlinelearning.dto.course.*;
import com.onlinelearning.service.ExpertLessonService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.List;
@RestController @RequiredArgsConstructor @RequestMapping("/api/v1/expert/courses/{courseId}/lessons")
public class ExpertLessonController {
    private final ExpertLessonService lessons;
    @GetMapping public List<LessonResponse> list(@PathVariable Long courseId, Authentication a) { return lessons.list(courseId, a.getName()); }
    @GetMapping("/{id}") public LessonResponse get(@PathVariable Long courseId, @PathVariable Long id, Authentication a) { return lessons.get(courseId, id, a.getName()); }
    @PostMapping @ResponseStatus(HttpStatus.CREATED) public LessonResponse create(@PathVariable Long courseId, @Valid @RequestBody LessonRequest r, Authentication a) { return lessons.create(courseId, a.getName(), r); }
    @PutMapping("/{id}") public LessonResponse update(@PathVariable Long courseId, @PathVariable Long id, @Valid @RequestBody LessonRequest r, Authentication a) { return lessons.update(courseId, id, a.getName(), r); }
    @DeleteMapping("/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) public void delete(@PathVariable Long courseId, @PathVariable Long id, Authentication a) { lessons.delete(courseId, id, a.getName()); }
}

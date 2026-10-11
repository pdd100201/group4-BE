package com.onlinelearning.controller;

import com.onlinelearning.dto.course.*;
import com.onlinelearning.entity.CourseStatus;
import com.onlinelearning.service.ExpertCourseService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.HttpStatus;
import java.util.List;

@RestController @RequiredArgsConstructor @RequestMapping("/api/v1/expert/courses")
public class ExpertCourseController {
    private final ExpertCourseService courses;
    @ExceptionHandler(org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public java.util.Map<String, String> invalidParameter() { return java.util.Map.of("message", "Tham số truy vấn không hợp lệ."); }
    public record CoursePage(List<ExpertCourseResponse> content, long totalElements, int totalPages, int number) {}
    @GetMapping public CoursePage list(Authentication actor, @RequestParam(defaultValue="") String search,
        @RequestParam(defaultValue="") String category, @RequestParam(required=false) CourseStatus status,
        @RequestParam(defaultValue="0") int page, @RequestParam(defaultValue="10") int size) {
        if (page < 0 || size < 1 || size > 100) throw new com.onlinelearning.exception.ApiException(HttpStatus.BAD_REQUEST, "Phân trang không hợp lệ.");
        var result = courses.list(actor.getName(), search, category, status, page, size);
        return new CoursePage(result.getContent(), result.getTotalElements(), result.getTotalPages(), result.getNumber());
    }
    @GetMapping("/{id}") public ExpertCourseResponse get(@PathVariable Long id, Authentication actor) { return courses.get(id, actor.getName()); }
    @PostMapping @ResponseStatus(HttpStatus.CREATED) public ExpertCourseResponse create(@Valid @RequestBody CourseRequest request, Authentication actor) { return courses.create(actor.getName(), request); }
    @PutMapping("/{id}") public ExpertCourseResponse update(@PathVariable Long id, @Valid @RequestBody CourseRequest request, Authentication actor) { return courses.update(id, actor.getName(), request); }
    @DeleteMapping("/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) public void delete(@PathVariable Long id, Authentication actor) { courses.delete(id, actor.getName()); }
}

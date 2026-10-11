package com.onlinelearning.controller;

import com.onlinelearning.dto.course.ManagerCourseResponse;
import com.onlinelearning.entity.CourseStatus;
import com.onlinelearning.exception.ApiException;
import com.onlinelearning.service.ManagerCourseService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;

@RestController @RequiredArgsConstructor @RequestMapping("/api/v1/manager/courses")
public class ManagerCourseController {
    private final ManagerCourseService courses;
    public record CoursePage(List<ManagerCourseResponse> content, long totalElements, int totalPages, int number) {}
    @GetMapping public CoursePage list(@RequestParam(defaultValue="") String search, @RequestParam(defaultValue="") String category,
        @RequestParam(required=false) String status, @RequestParam(defaultValue="0") int page, @RequestParam(defaultValue="10") int size) {
        if (page < 0 || size < 1 || size > 100) throw new ApiException(HttpStatus.BAD_REQUEST, "Phân trang không hợp lệ.");
        CourseStatus parsed = status == null ? CourseStatus.PENDING_REVIEW : null;
        if (status != null && !status.isBlank()) {
            try { parsed = CourseStatus.valueOf(status); } catch (IllegalArgumentException e) { throw new ApiException(HttpStatus.BAD_REQUEST, "Trạng thái không hợp lệ."); }
        }
        var result = courses.list(search, category, parsed, page, size);
        return new CoursePage(result.getContent(), result.getTotalElements(), result.getTotalPages(), result.getNumber());
    }
    @GetMapping("/{id}") public ManagerCourseResponse get(@PathVariable Long id) { return courses.get(id); }
    @ExceptionHandler(org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST) public Map<String, String> invalidParameter() { return Map.of("message", "Tham số truy vấn không hợp lệ."); }
}

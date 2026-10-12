package com.onlinelearning.controller;

import com.onlinelearning.dto.course.*;
import com.onlinelearning.service.CoursePackageService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController @RequiredArgsConstructor @RequestMapping("/api/v1/expert/courses/{courseId}/packages")
public class ExpertCoursePackageController {
    private final CoursePackageService packages;
    @GetMapping public List<CoursePackageResponse> get(@PathVariable Long courseId, Authentication actor) { return packages.get(courseId,actor.getName()); }
    @PutMapping public List<CoursePackageResponse> update(@PathVariable Long courseId,Authentication actor,@Valid @RequestBody CoursePackagesRequest request) {
        return packages.update(courseId,actor.getName(),request);
    }
}

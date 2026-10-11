package com.onlinelearning.controller;

import com.onlinelearning.dto.course.CourseWorkflowResponse;
import com.onlinelearning.service.CourseModerationService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController @RequiredArgsConstructor
public class CourseModerationController {
    private final CourseModerationService moderation;
    @PostMapping({"/api/v1/admin/courses/{id}/archive", "/api/v1/manager/courses/{id}/archive"})
    public CourseWorkflowResponse archive(@PathVariable Long id, @Valid @RequestBody ReasonRequest request, Authentication actor) {
        return CourseWorkflowResponse.from(moderation.archive(id, actor.getName(), request.reason()));
    }
    @DeleteMapping({"/api/v1/admin/courses/{id}", "/api/v1/manager/courses/{id}"})
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id, @Valid @RequestBody ReasonRequest request, Authentication actor) {
        moderation.delete(id, actor.getName(), request.reason());
    }
    public record ReasonRequest(@NotBlank(message="Lý do là bắt buộc") @Size(max=255, message="Lý do tối đa 255 ký tự") String reason) {}
}

package com.onlinelearning.controller;

import com.onlinelearning.dto.course.CourseWorkflowResponse;
import com.onlinelearning.service.CourseRestorationService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
public class CourseRestorationController {
    private final CourseRestorationService restoration;

    @PostMapping({"/api/v1/admin/courses/{id}/restore", "/api/v1/manager/courses/{id}/restore"})
    public CourseWorkflowResponse restore(@PathVariable Long id, @Valid @RequestBody RestoreRequest request,
                                          Authentication actor) {
        return CourseWorkflowResponse.from(restoration.restore(id, actor.getName(), request.reason()));
    }

    public record RestoreRequest(
        @NotBlank(message = "Lý do khôi phục là bắt buộc")
        @Size(max = 255, message = "Lý do khôi phục tối đa 255 ký tự") String reason) {}
}

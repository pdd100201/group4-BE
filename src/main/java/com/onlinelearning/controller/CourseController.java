package com.onlinelearning.controller;

import com.onlinelearning.dto.course.CourseWorkflowResponse;
import com.onlinelearning.service.CourseService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class CourseController {
    private final CourseService courses;

    @PostMapping("/api/v1/expert/courses/{id}/submit")
    public CourseWorkflowResponse submit(@PathVariable Long id, Authentication actor) {
        return CourseWorkflowResponse.from(courses.submit(id, actor.getName()));
    }

    @PostMapping("/api/v1/expert/courses/{id}/resubmit")
    public CourseWorkflowResponse resubmit(@PathVariable Long id, Authentication actor) {
        return CourseWorkflowResponse.from(courses.resubmit(id, actor.getName()));
    }

    @PostMapping("/api/v1/manager/courses/{id}/approve")
    public CourseWorkflowResponse approve(@PathVariable Long id, Authentication actor) {
        return CourseWorkflowResponse.from(courses.approve(id, actor.getName()));
    }

    @PostMapping("/api/v1/manager/courses/{id}/reject")
    public CourseWorkflowResponse reject(@PathVariable Long id, @RequestBody RejectCourse request, Authentication actor) {
        return CourseWorkflowResponse.from(courses.reject(id, actor.getName(), request.reason()));
    }

    public record RejectCourse(String reason) {}
}

package com.onlinelearning.controller;
import com.onlinelearning.service.CourseAnnouncementService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController @RequiredArgsConstructor @RequestMapping("/api/v1/expert/courses/{courseId}/announcements")
public class CourseAnnouncementController {
 private final CourseAnnouncementService announcements;
 public record Request(
  @NotBlank @Pattern(regexp="[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}") String requestKey,
  @NotBlank @Size(max=200) String title,@NotBlank @Size(max=1000) String message) {}
 @GetMapping public Page<CourseAnnouncementService.View> history(Authentication actor,@PathVariable Long courseId,@RequestParam(defaultValue="0") int page){return announcements.history(actor.getName(),courseId,page);}
 @PostMapping public CourseAnnouncementService.View create(Authentication actor,@PathVariable Long courseId,@Valid @RequestBody Request request){return announcements.create(actor.getName(),courseId,request.requestKey(),request.title(),request.message());}
}
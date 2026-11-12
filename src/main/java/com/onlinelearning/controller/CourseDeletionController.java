package com.onlinelearning.controller;

import com.onlinelearning.service.CourseDeletionService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import java.security.Principal;
import java.time.Instant;

@RestController @RequiredArgsConstructor
public class CourseDeletionController {
    private final CourseDeletionService deletion;
    public record Request(@NotBlank @Size(max=1000) String reason) {}
    public record Review(boolean approve,@Size(max=1000) String reason,@NotNull Instant expectedRequestedAt) {}
    @PostMapping("/api/v1/expert/courses/{id}/deletion-request") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void request(@PathVariable Long id,@Valid @RequestBody Request body,Principal actor) { deletion.request(id,actor.getName(),body.reason()); }
    @GetMapping("/api/v1/manager/course-deletion-requests")
    public Page<CourseDeletionService.RequestView> list(@RequestParam(defaultValue="PENDING") String status,@RequestParam(defaultValue="0") int page,
        @RequestParam(defaultValue="10") int size,Principal actor) { return deletion.list(actor.getName(),status,page,size); }
    @PostMapping("/api/v1/manager/course-deletion-requests/{id}/review")
    public CourseDeletionService.RequestView review(@PathVariable Long id,@Valid @RequestBody Review body,Principal actor) {
        return deletion.review(id,actor.getName(),body.approve(),body.reason(),body.expectedRequestedAt());
    }
}

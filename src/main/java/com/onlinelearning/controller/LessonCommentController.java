package com.onlinelearning.controller;

import com.onlinelearning.service.LessonCommentService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController @RequiredArgsConstructor
@RequestMapping("/api/v1/student/lessons/{lessonId}/comments")
public class LessonCommentController {
    private final LessonCommentService service;
    public record CommentRequest(@NotBlank @Size(max=2000) String content,@Positive Long parentId) {}
    public record LikeRequest(@NotNull Boolean liked) {}
    public record EditRequest(@NotBlank @Size(max=2000) String content) {}
    @PutMapping("/{commentId}") public LessonCommentService.CommentView update(Authentication actor,
        @PathVariable Long lessonId,@PathVariable Long commentId,@Valid @RequestBody EditRequest body) {
        return service.update(actor.getName(),lessonId,commentId,body.content());
    }
    @DeleteMapping("/{commentId}") @ResponseStatus(org.springframework.http.HttpStatus.NO_CONTENT)
    public void delete(Authentication actor,@PathVariable Long lessonId,@PathVariable Long commentId) {
        service.delete(actor.getName(),lessonId,commentId);
    }
    @GetMapping public Page<LessonCommentService.CommentView> list(Authentication actor,@PathVariable Long lessonId,
        @RequestParam(required=false) Long parentId,@RequestParam(defaultValue="0") int page) {
        return service.list(actor.getName(),lessonId,parentId,page);
    }
    @PostMapping public LessonCommentService.CommentView create(Authentication actor,@PathVariable Long lessonId,
        @Valid @RequestBody CommentRequest body) {
        return service.create(actor.getName(),lessonId,body.parentId(),body.content());
    }
    @PutMapping("/{commentId}/like") public LessonCommentService.CommentView like(Authentication actor,
        @PathVariable Long lessonId,@PathVariable Long commentId,@Valid @RequestBody LikeRequest body) {
        return service.like(actor.getName(),lessonId,commentId,body.liked());
    }
}

package com.onlinelearning.controller;

import com.onlinelearning.dto.blog.*;
import com.onlinelearning.service.CommentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController @RequiredArgsConstructor @RequestMapping("/api/v1")
public class CommentController {
    private final CommentService comments;
    @GetMapping("/public/posts/{postId}/comments")
    public BlogPage<CommentResponse> list(@PathVariable Long postId, Authentication actor,
        @RequestParam(defaultValue="0") int page, @RequestParam(defaultValue="10") int size) {
        return comments.list(postId, actor == null ? null : actor.getName(), page, size);
    }
    @GetMapping("/public/posts/{postId}/comments/{id}")
    public CommentResponse detail(@PathVariable Long postId, @PathVariable Long id, Authentication actor) {
        return comments.detail(postId, id, actor == null ? null : actor.getName());
    }
    @GetMapping({"/manager/posts/{postId}/comments", "/admin/posts/{postId}/comments"})
    public BlogPage<CommentResponse> moderationList(@PathVariable Long postId, Authentication actor,
        @RequestParam(required=false) com.onlinelearning.entity.CommentStatus status,
        @RequestParam(defaultValue="0") int page, @RequestParam(defaultValue="10") int size) {
        return comments.moderationList(postId, actor.getName(), status, page, size);
    }
    @GetMapping({"/manager/posts/{postId}/comments/{id}", "/admin/posts/{postId}/comments/{id}"})
    public CommentResponse moderationDetail(@PathVariable Long postId, @PathVariable Long id, Authentication actor) {
        return comments.moderationDetail(postId, id, actor.getName());
    }
    @PostMapping("/student/posts/{postId}/comments") @ResponseStatus(HttpStatus.CREATED)
    public CommentResponse create(@PathVariable Long postId, @Valid @RequestBody CommentRequest request, Authentication actor) {
        return comments.create(postId, actor.getName(), request);
    }
    @PutMapping("/student/comments/{id}")
    public CommentResponse update(@PathVariable Long id, @Valid @RequestBody CommentRequest request, Authentication actor) {
        return comments.update(id, actor.getName(), request);
    }
    @DeleteMapping("/comments/{id}") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id, Authentication actor) { comments.delete(id, actor.getName()); }
    @PatchMapping("/comments/{id}/status")
    public CommentResponse status(@PathVariable Long id, @Valid @RequestBody CommentStatusRequest request, Authentication actor) {
        return comments.status(id, actor.getName(), com.onlinelearning.entity.CommentStatus.valueOf(request.status()));
    }
}

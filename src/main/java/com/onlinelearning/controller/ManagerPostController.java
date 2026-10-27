package com.onlinelearning.controller;

import com.onlinelearning.dto.blog.*;
import com.onlinelearning.service.ManagerPostService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController @RequiredArgsConstructor @RequestMapping("/api/v1/manager/posts")
public class ManagerPostController {
    private final ManagerPostService posts;
    @GetMapping public BlogPage<ManagedPostResponse> list(Authentication actor, @RequestParam(defaultValue="0") int page,
        @RequestParam(defaultValue="10") int size) { return posts.list(actor.getName(), page, size); }
    @GetMapping("/{id}") public ManagedPostResponse detail(@PathVariable Long id, Authentication actor) { return posts.detail(id, actor.getName()); }
    @PostMapping @ResponseStatus(HttpStatus.CREATED)
    public ManagedPostResponse create(@Valid @RequestBody PostRequest request, Authentication actor) { return posts.create(actor.getName(), request); }
    @PutMapping("/{id}") public ManagedPostResponse update(@PathVariable Long id, @Valid @RequestBody PostRequest request, Authentication actor) {
        return posts.update(id, actor.getName(), request);
    }
    @PatchMapping("/{id}/review") public ManagedPostResponse review(@PathVariable Long id,
        @Valid @RequestBody PostReviewRequest request, Authentication actor) {
        return posts.review(id, actor.getName(), request.decision());
    }
    @DeleteMapping("/{id}") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id, Authentication actor) { posts.delete(id, actor.getName()); }
}

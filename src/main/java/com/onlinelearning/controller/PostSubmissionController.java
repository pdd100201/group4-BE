package com.onlinelearning.controller;

import com.onlinelearning.dto.blog.BlogPage;
import com.onlinelearning.dto.blog.ManagedPostResponse;
import com.onlinelearning.dto.blog.PostSubmissionRequest;
import com.onlinelearning.service.PostSubmissionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController @RequiredArgsConstructor @RequestMapping("/api/v1/posts/submissions")
public class PostSubmissionController {
    private final PostSubmissionService submissions;

    @PostMapping @ResponseStatus(HttpStatus.CREATED)
    public ManagedPostResponse submit(@Valid @RequestBody PostSubmissionRequest request, Authentication actor) {
        return submissions.submit(actor.getName(), request);
    }

    @GetMapping("/mine")
    public BlogPage<ManagedPostResponse> mine(Authentication actor,
        @RequestParam(defaultValue="0") int page, @RequestParam(defaultValue="10") int size) {
        return submissions.mine(actor.getName(), page, size);
    }
}

package com.onlinelearning.controller;

import com.onlinelearning.dto.blog.*;
import com.onlinelearning.service.PublicPostService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController @RequiredArgsConstructor @RequestMapping("/api/v1/public/posts")
public class PublicPostController {
    private final PublicPostService posts;
    @GetMapping public BlogPage<PostResponse> list(@RequestParam(defaultValue="") String search,
        @RequestParam(defaultValue="") String category, @RequestParam(defaultValue="0") int page,
        @RequestParam(defaultValue="9") int size) { return posts.list(search, category, page, size); }
    @GetMapping("/categories") public List<String> categories() { return posts.categories(); }
    @GetMapping("/{id}") public PostDetailResponse detail(@PathVariable Long id) { return posts.detail(id); }
}

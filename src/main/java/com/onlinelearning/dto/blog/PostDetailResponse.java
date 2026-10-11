package com.onlinelearning.dto.blog;

import com.onlinelearning.entity.Post;

public record PostDetailResponse(PostResponse post, String content) {
    public static PostDetailResponse from(Post p) { return new PostDetailResponse(PostResponse.from(p), p.getContent()); }
}

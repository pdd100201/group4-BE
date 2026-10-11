package com.onlinelearning.dto.blog;

import com.onlinelearning.entity.Post;

public record ManagedPostResponse(PostResponse post, String content, boolean published) {
    public static ManagedPostResponse from(Post p) { return new ManagedPostResponse(PostResponse.from(p), p.getContent(), p.isPublished()); }
}

package com.onlinelearning.dto.blog;

import com.onlinelearning.entity.Post;
import com.onlinelearning.entity.PostStatus;

public record ManagedPostResponse(PostResponse post, String content, boolean published, PostStatus status) {
    public static ManagedPostResponse from(Post p) { return new ManagedPostResponse(PostResponse.from(p), p.getContent(), p.isPublished(), p.getStatus()); }
}

package com.onlinelearning.dto.blog;

import com.onlinelearning.entity.Post;
import com.onlinelearning.entity.PostStatus;

public record ManagedPostResponse(PostResponse post, String content, boolean published, PostStatus status) {
    public static ManagedPostResponse from(Post p) {
        boolean publicPost = p.isPublished() && p.getStatus() == PostStatus.PUBLISHED && !p.isDeleted();
        return new ManagedPostResponse(PostResponse.from(p), p.getContent(), publicPost, p.getStatus());
    }
}

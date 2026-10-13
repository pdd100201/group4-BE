package com.onlinelearning.dto.blog;

import com.onlinelearning.entity.Post;
import java.time.Instant;

public record PostResponse(Long id, String title, String category, String thumbnailUrl, String summary,
                           String authorName, Instant createdAt, Instant updatedAt) {
    public static PostResponse from(Post p) {
        return new PostResponse(p.getId(), p.getTitle(), p.getCategory(), p.getThumbnailUrl(), p.getSummary(),
            p.getAuthor().getFullName(), p.getCreatedAt(), p.getUpdatedAt());
    }
}

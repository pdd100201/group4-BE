package com.onlinelearning.dto.blog;

import com.onlinelearning.entity.*;
import java.time.Instant;

public record CommentResponse(Long id, Long postId, String postTitle, String authorName, String avatarUrl,
    String content, CommentStatus status, Instant createdAt, Instant updatedAt, boolean canEdit, boolean canDelete) {
    public static CommentResponse from(PostComment c, User viewer) {
        boolean available = c.getStatus() == CommentStatus.VISIBLE && c.getPost().isPublished() && !c.getPost().isDeleted();
        boolean owner = viewer != null && viewer.isEnabled() && !viewer.isBlocked()
            && viewer.getId().equals(c.getAuthor().getId()) && viewer.getRoles().stream().anyMatch(r -> "STUDENT".equals(r.getCode()));
        boolean moderator = viewer != null && viewer.isEnabled() && !viewer.isBlocked()
            && viewer.getRoles().stream().anyMatch(r -> "MANAGER".equals(r.getCode()) || "ADMIN".equals(r.getCode()));
        return new CommentResponse(c.getId(), c.getPost().getId(), c.getPost().getTitle(), c.getAuthor().getFullName(),
            c.getAuthor().getAvatarUrl(), c.getContent(), c.getStatus(), c.getCreatedAt(), c.getUpdatedAt(),
            available && owner, c.getStatus() != CommentStatus.DELETED && (moderator || available && owner));
    }
}

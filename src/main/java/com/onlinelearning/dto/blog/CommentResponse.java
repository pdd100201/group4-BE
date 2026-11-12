package com.onlinelearning.dto.blog;

import com.onlinelearning.entity.*;
import java.time.Instant;

public record CommentResponse(Long id, Long postId, String postTitle, String authorName, String avatarUrl,
    String content, CommentStatus status, Instant createdAt, Instant updatedAt, boolean canEdit, boolean canDelete, long likeCount, boolean liked, boolean canLike,Long parentId,long replyCount) {
    public static CommentResponse from(PostComment c, User viewer) {
        return from(c,viewer,0,false,0);
    }
    public static CommentResponse from(PostComment c, User viewer, long likeCount, boolean liked,long replyCount) {
        boolean available = c.getStatus() == CommentStatus.VISIBLE && c.getPost().isPublished() && !c.getPost().isDeleted();
        boolean participant = viewer != null && viewer.isEnabled() && !viewer.isBlocked()
            && viewer.getRoles().stream().anyMatch(r -> java.util.Set.of("STUDENT","EXPERT","MANAGER","ADMIN").contains(r.getCode()));
        boolean owner = participant && viewer.getId().equals(c.getAuthor().getId());
        boolean moderator = viewer != null && viewer.isEnabled() && !viewer.isBlocked()
            && viewer.getRoles().stream().anyMatch(r -> "MANAGER".equals(r.getCode()) || "ADMIN".equals(r.getCode()));
        return new CommentResponse(c.getId(), c.getPost().getId(), c.getPost().getTitle(), c.getAuthor().getFullName(),
            c.getAuthor().getAvatarUrl(), c.getContent(), c.getStatus(), c.getCreatedAt(), c.getUpdatedAt(),
            available && owner, c.getStatus() != CommentStatus.DELETED && (moderator || available && owner),likeCount,liked,
            available && participant,c.getParentId(),replyCount);
    }
}

package com.onlinelearning.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;

@Entity @DiscriminatorValue("POST_COMMENT") @Getter @Setter @NoArgsConstructor
public class PostComment extends DiscussionEntry {
    @ManyToOne( fetch=FetchType.LAZY) private Post post;
    @Enumerated(EnumType.STRING) private CommentStatus status=CommentStatus.VISIBLE;
    @PrePersist void created() {
        if (getCreatedAt() == null) setCreatedAt(Instant.now());
        if (getUpdatedAt() == null) setUpdatedAt(getCreatedAt());
    }
}

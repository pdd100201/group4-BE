package com.onlinelearning.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;

@Entity @Getter @Setter @NoArgsConstructor
public class PostComment extends BaseEntity {
    @ManyToOne(optional=false, fetch=FetchType.LAZY) private Post post;
    @ManyToOne(optional=false, fetch=FetchType.LAZY) private User author;
    @Column(nullable=false,length=2000) private String content;
    @Enumerated(EnumType.STRING) private CommentStatus status=CommentStatus.VISIBLE;
    private Instant createdAt;
    private Instant updatedAt;
    @PrePersist void created() {
        if (createdAt == null) createdAt = Instant.now();
        if (updatedAt == null) updatedAt = createdAt;
    }
}

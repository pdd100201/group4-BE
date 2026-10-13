package com.onlinelearning.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;
import java.util.*;

@Entity @Table(name="posts") @Getter @Setter @NoArgsConstructor
public class Post extends BaseEntity {
    @ManyToOne(optional=false, fetch=FetchType.LAZY) private User author;
    @Column(nullable=false) private String title;
    private String category;
    private String thumbnailUrl;
    @Column(length=1000) private String summary;
    @Column(nullable=false,length=12000) private String content;
    private boolean featured;
    private boolean published;
    @Column(nullable=false) private boolean deleted;
    private Instant createdAt;
    private Instant updatedAt;
    @OneToMany(mappedBy="post") private List<PostComment> comments=new ArrayList<>();

    @PrePersist void created() {
        if (createdAt == null) createdAt = Instant.now();
        if (updatedAt == null) updatedAt = createdAt;
    }
}

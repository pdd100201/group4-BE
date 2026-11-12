package com.onlinelearning.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;
import java.text.Normalizer;
import java.util.*;

@Entity @Table(name="posts") @Getter @Setter @NoArgsConstructor
public class Post extends BaseEntity {
    @ManyToOne(optional=false, fetch=FetchType.LAZY) private User author;
    @Column(nullable=false) private String title;
    @Column(nullable=false, unique=true, length=280) private String slug;
    private String category;
    private String thumbnailUrl;
    @Column(length=1000) private String summary;
    @Column(nullable=false,length=12000) private String content;
    private boolean featured;
    private boolean published;
    @Enumerated(EnumType.STRING) @Column(nullable=false,length=32) private PostStatus status=PostStatus.DRAFT;
    private Instant publishedAt;
    @Column(nullable=false) private boolean deleted;
    private Instant createdAt;
    private Instant updatedAt;
    @OneToMany(mappedBy="post") private List<PostComment> comments=new ArrayList<>();

    @PrePersist void created() {
        if (slug == null || slug.isBlank()) {
            String prefix = Normalizer.normalize(title == null ? "" : title, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "").replace('đ', 'd').replace('Đ', 'D')
                .toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-")
                .replaceAll("^-+|-+$", "");
            if (prefix.isEmpty()) prefix = "post";
            // Keep room for a UUID so repeated titles can be inserted concurrently.
            if (prefix.length() > 243) prefix = prefix.substring(0, 243).replaceAll("-+$", "");
            slug = prefix + "-" + UUID.randomUUID();
        }
        if (createdAt == null) createdAt = Instant.now();
        if (updatedAt == null) updatedAt = createdAt;
    }
}

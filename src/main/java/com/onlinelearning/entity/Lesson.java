package com.onlinelearning.entity;
import jakarta.persistence.*; import lombok.*;
@Entity @Getter @Setter @NoArgsConstructor
public class Lesson extends BaseEntity {
    @ManyToOne private MediaAsset video;
    @ManyToOne(optional=false) private Course course;
    @Column(nullable=false) private String title;
    @Column(length=8000) private String content;
    private Integer position;
    private boolean active=true;

    @org.hibernate.annotations.CreationTimestamp
    @Column(name="created_at", nullable=false, updatable=false)
    private java.time.Instant createdAt;

    @org.hibernate.annotations.UpdateTimestamp
    @Column(name="updated_at", nullable=false)
    private java.time.Instant updatedAt;
}

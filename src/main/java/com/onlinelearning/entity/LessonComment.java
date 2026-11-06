package com.onlinelearning.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;

@Entity @Table(name="lesson_comments", indexes={
    @Index(name="idx_lesson_comment_thread", columnList="lesson_id,parent_id,id")})
@Getter @Setter @NoArgsConstructor
public class LessonComment extends BaseEntity {
    @ManyToOne(optional=false, fetch=FetchType.LAZY) private Lesson lesson;
    @ManyToOne(optional=false, fetch=FetchType.LAZY) private User author;
    @ManyToOne(fetch=FetchType.LAZY) private LessonComment parent;
    @Column(nullable=false, length=2000) private String content;
    @Column(nullable=false, updatable=false) private Instant createdAt=Instant.now();
    private Instant updatedAt;
    @Column(nullable=false) private boolean deleted;
}

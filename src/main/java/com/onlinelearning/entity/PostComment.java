package com.onlinelearning.entity;
import jakarta.persistence.*; import lombok.*;
@Entity @Getter @Setter @NoArgsConstructor
public class PostComment extends BaseEntity { @ManyToOne(optional=false) private Post post; @ManyToOne(optional=false) private User author; @Column(nullable=false,length=2000) private String content; @Enumerated(EnumType.STRING) private CommentStatus status=CommentStatus.VISIBLE; }

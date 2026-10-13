package com.onlinelearning.entity;
import jakarta.persistence.*; import lombok.*;
@Entity @Getter @Setter @NoArgsConstructor
public class QuestionOption extends BaseEntity { @ManyToOne(optional=false) private Question question; @Column(nullable=false) private String content; private boolean correct;
    @org.hibernate.annotations.CreationTimestamp @Column(nullable=false, updatable=false) private java.time.Instant createdAt;
    @org.hibernate.annotations.UpdateTimestamp @Column(nullable=false) private java.time.Instant updatedAt;
}

package com.onlinelearning.entity;
import jakarta.persistence.*; import lombok.*;
@Entity @Getter @Setter @NoArgsConstructor
public class QuestionOption extends BaseEntity { @ManyToOne(optional=false) private Question question; @Column(nullable=false) private String content; private boolean correct; }

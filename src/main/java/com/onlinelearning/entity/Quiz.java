package com.onlinelearning.entity;
import jakarta.persistence.*; import lombok.*; import java.util.*;
@Entity @Getter @Setter @NoArgsConstructor
public class Quiz extends BaseEntity { @ManyToOne(optional=false) private Course course; @Column(nullable=false) private String title; private Integer durationMinutes; private Integer passRate; private Integer maxAttempts; private boolean active=true; @OneToMany(mappedBy="quiz") private List<QuizQuestion> quizQuestions=new ArrayList<>(); }

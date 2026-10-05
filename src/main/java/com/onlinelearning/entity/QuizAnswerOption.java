package com.onlinelearning.entity;
import jakarta.persistence.*; import lombok.*;
@Entity @Getter @Setter @NoArgsConstructor
public class QuizAnswerOption extends BaseEntity { @ManyToOne(optional=false) private QuizAnswer quizAnswer; @ManyToOne(optional=false) private QuestionOption questionOption; }

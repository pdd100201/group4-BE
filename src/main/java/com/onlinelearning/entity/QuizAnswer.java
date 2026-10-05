package com.onlinelearning.entity;
import jakarta.persistence.*; import lombok.*; import java.util.*;
@Entity @Getter @Setter @NoArgsConstructor
public class QuizAnswer extends BaseEntity { @ManyToOne(optional=false) private QuizAttempt attempt; @ManyToOne(optional=false) private Question question; private String textAnswer; private boolean correct; private Double awardedPoints; @OneToMany(mappedBy="quizAnswer",cascade=CascadeType.ALL) private List<QuizAnswerOption> selectedOptions=new ArrayList<>(); }

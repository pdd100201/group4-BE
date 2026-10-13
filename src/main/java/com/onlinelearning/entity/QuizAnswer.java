package com.onlinelearning.entity;
import jakarta.persistence.*; import lombok.*; import java.util.*;
@Entity @Getter @Setter @NoArgsConstructor
public class QuizAnswer extends BaseEntity { @ManyToOne(optional=false) private QuizAttempt attempt; @ManyToOne(optional=false) private Question question; private String textAnswer; private boolean correct; private Double awardedPoints; @OneToMany(mappedBy="quizAnswer",cascade=CascadeType.ALL,orphanRemoval=true) private List<QuizAnswerOption> selectedOptions=new ArrayList<>();
    @org.hibernate.annotations.CreationTimestamp @Column(nullable=false,updatable=false) private java.time.Instant createdAt;
    @org.hibernate.annotations.UpdateTimestamp @Column(nullable=false) private java.time.Instant updatedAt;
}

package com.onlinelearning.entity;
import jakarta.persistence.*; import lombok.*; import java.time.*; import java.util.*;
@Entity @Getter @Setter @NoArgsConstructor
public class QuizAttempt extends BaseEntity { @ManyToOne(optional=false) private Quiz quiz; @ManyToOne(optional=false) private User student; private Instant startedAt=Instant.now(); private Instant submittedAt; private Double score; private boolean passed; @OneToMany(mappedBy="attempt",cascade=CascadeType.ALL) private List<QuizAnswer> answers=new ArrayList<>(); }

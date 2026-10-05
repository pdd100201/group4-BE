package com.onlinelearning.entity;
import jakarta.persistence.*; import lombok.*;
@Entity @Table(uniqueConstraints=@UniqueConstraint(columnNames={"quiz_id","question_id"})) @Getter @Setter @NoArgsConstructor
public class QuizQuestion extends BaseEntity { @ManyToOne(optional=false) private Quiz quiz; @ManyToOne(optional=false) private Question question; private Integer position; private Integer points=1; }

package com.onlinelearning.entity;
import jakarta.persistence.*; import lombok.*; import java.time.*;
@Entity @Table(uniqueConstraints=@UniqueConstraint(columnNames={"student_id","lesson_id"})) @Getter @Setter @NoArgsConstructor
public class StudentLessonProgress extends BaseEntity { @ManyToOne(optional=false) private User student; @ManyToOne(optional=false) private Lesson lesson; private boolean completed; private Integer progressPercent; private Instant completedAt; }

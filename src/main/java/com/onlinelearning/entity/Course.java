package com.onlinelearning.entity;
import jakarta.persistence.*; import lombok.*; import java.math.*; import java.util.*;
@Entity @Getter @Setter @NoArgsConstructor
public class Course extends BaseEntity { @Column(nullable=false) private String title; @Column(length=4000) private String description; private String category; private BigDecimal price; @Enumerated(EnumType.STRING) @Column(nullable=false) private CourseStatus status=CourseStatus.DRAFT; @ManyToOne(optional=false) @JoinColumn(name="expert_id") private User expert; @ManyToOne private User reviewedBy; private String rejectionReason; @OneToMany(mappedBy="course") private List<Lesson> lessons=new ArrayList<>(); }

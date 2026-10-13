package com.onlinelearning.entity;
import jakarta.persistence.*; import lombok.*; import java.math.*; import java.util.*;
@Entity @Getter @Setter @NoArgsConstructor
public class Course extends BaseEntity { @ManyToOne private MediaAsset thumbnail; @Column(nullable=false) private String title; @Column(length=4000) private String description; private String category; private BigDecimal price; @Enumerated(EnumType.STRING) @Column(nullable=false) private CourseStatus status=CourseStatus.DRAFT; @ManyToOne(optional=false) @JoinColumn(name="expert_id") private User expert; @ManyToOne private User reviewedBy; private String rejectionReason; @OneToMany(mappedBy="course") private List<Lesson> lessons=new ArrayList<>();
    @org.hibernate.annotations.CreationTimestamp
    @Column(name="created_at", nullable=false, updatable=false)
    private java.time.Instant createdAt;

    @org.hibernate.annotations.UpdateTimestamp
    @Column(name="updated_at", nullable=false)
    private java.time.Instant updatedAt;
}

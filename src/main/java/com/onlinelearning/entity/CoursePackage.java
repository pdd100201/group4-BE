package com.onlinelearning.entity;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;

@Entity @Getter @Setter @NoArgsConstructor
@Table(uniqueConstraints = @UniqueConstraint(name="uk_course_package_duration", columnNames={"course_id", "duration"}))
public class CoursePackage extends BaseEntity {
    @ManyToOne(optional=false,fetch=FetchType.LAZY) private Course course;
    @Enumerated(EnumType.STRING) @Column(nullable=false, length=20) private PackageDuration duration;
    @Column(nullable=false, precision=12, scale=0) private BigDecimal price;
    @Column(nullable=false, precision=5, scale=2) private BigDecimal discountPercent = BigDecimal.ZERO;
    @Column(nullable=false) private boolean active = true;
}

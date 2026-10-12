package com.onlinelearning.entity;
import jakarta.persistence.*; import lombok.*; import java.time.*; import java.math.*;
@Entity @Getter @Setter @NoArgsConstructor
public class CourseRegistration extends BaseEntity {
 @ManyToOne(optional=false) private User student;
 @ManyToOne(optional=false) private Course course;
 @Enumerated(EnumType.STRING) private RegistrationStatus status=RegistrationStatus.PENDING_PAYMENT;
 private BigDecimal amount;
 private Instant validFrom;
 private Instant validUntil;
 private String note;
 private Instant createdAt;
 private String packageNameSnapshot;
 private BigDecimal priceSnapshot;
 private BigDecimal discountSnapshot;
 private Integer validityDaysSnapshot;
 @ManyToOne private CoursePackage coursePackage;
 @Enumerated(EnumType.STRING) private PackageDuration packageDurationSnapshot;
 private Integer validityMonthsSnapshot;
 private Boolean lifetimeSnapshot;
 @Column(precision=5,scale=2) private BigDecimal discountPercentSnapshot;
 private String studentNameSnapshot;
 private String studentEmailSnapshot;
 private String studentPhoneSnapshot;
 @PrePersist void recordCreationTime() { if (createdAt == null) createdAt = Instant.now(); }
}

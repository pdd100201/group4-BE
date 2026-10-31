package com.onlinelearning.entity;
import jakarta.persistence.*; import lombok.*; import java.math.*; import java.time.Instant;
@Entity @Getter @Setter @NoArgsConstructor
public class Payment extends BaseEntity {
    @OneToOne(optional=false) private CourseRegistration registration;
    @Column(nullable=false) private BigDecimal amount;
    private String provider;
    @Column(unique=true) private String providerReference;
    @Enumerated(EnumType.STRING) private PaymentStatus status=PaymentStatus.PENDING;
    @Column(name="created_at", updatable=false) private Instant createdAt;
    @Column(name="updated_at") private Instant updatedAt;

    @PrePersist void recordCreationTime() {
        Instant now=Instant.now();
        if(createdAt==null) createdAt=now;
        updatedAt=now;
    }
    @PreUpdate void recordUpdateTime() { updatedAt=Instant.now(); }
}

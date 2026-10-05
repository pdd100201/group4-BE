package com.onlinelearning.entity;
import jakarta.persistence.*; import lombok.*; import java.math.*;
@Entity @Getter @Setter @NoArgsConstructor
public class Payment extends BaseEntity { @OneToOne(optional=false) private CourseRegistration registration; @Column(nullable=false) private BigDecimal amount; private String provider; @Column(unique=true) private String providerReference; @Enumerated(EnumType.STRING) private PaymentStatus status=PaymentStatus.PENDING; }

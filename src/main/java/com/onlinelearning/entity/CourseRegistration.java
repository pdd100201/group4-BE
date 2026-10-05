package com.onlinelearning.entity;
import jakarta.persistence.*; import lombok.*; import java.time.*; import java.math.*;
@Entity @Getter @Setter @NoArgsConstructor
public class CourseRegistration extends BaseEntity { @ManyToOne(optional=false) private User student; @ManyToOne(optional=false) private Course course; @Enumerated(EnumType.STRING) private RegistrationStatus status=RegistrationStatus.PENDING_PAYMENT; private BigDecimal amount; private Instant validFrom; private Instant validUntil; private String note; }

package com.onlinelearning.entity;
import jakarta.persistence.*; import lombok.*; import java.math.*;
@Entity @Table(name="payment_transactions") @Getter @Setter @NoArgsConstructor
public class Transaction extends BaseEntity {
 @ManyToOne(optional=false) private Payment payment;
 @Column(nullable=false) private BigDecimal amount;
 @Column(nullable=false) private String type;
 private String payload;
 private java.time.Instant createdAt;
 @PrePersist void recordCreationTime() { if (createdAt == null) createdAt = java.time.Instant.now(); }
}

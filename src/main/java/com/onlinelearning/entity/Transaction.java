package com.onlinelearning.entity;
import jakarta.persistence.*; import lombok.*; import java.math.*; import java.time.Instant;
@Entity @Table(name="payment_transactions") @Getter @Setter @NoArgsConstructor
public class Transaction extends BaseEntity {
 @ManyToOne(optional=false) private Payment payment;
 @Column(nullable=false) private BigDecimal amount;
 @Column(nullable=false) private String type;
 private String payload;
 @Column(name="created_at", nullable=false) private Instant createdAt;
 @Column(name="updated_at", nullable=false) private Instant updatedAt;
 @PrePersist void recordCreationTime() {
  Instant now=Instant.now();
  if (createdAt == null) createdAt=now;
  updatedAt=now;
 }
 @PreUpdate void recordUpdateTime() { updatedAt=Instant.now(); }
}

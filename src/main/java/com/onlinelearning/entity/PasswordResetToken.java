package com.onlinelearning.entity;
import jakarta.persistence.*; import lombok.*; import java.time.*;
@Entity @Getter @Setter @NoArgsConstructor
public class PasswordResetToken extends BaseEntity { @Column(nullable=false,unique=true) private String token; @ManyToOne(optional=false) private User user; @Column(nullable=false) private Instant expiresAt; private Instant usedAt; }

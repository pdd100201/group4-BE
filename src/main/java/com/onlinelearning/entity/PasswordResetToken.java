package com.onlinelearning.entity;
import jakarta.persistence.*; import lombok.*; import java.time.*;
@Entity @Table(name="password_reset_tokens") @Getter @Setter @NoArgsConstructor
public class PasswordResetToken extends BaseEntity { @Column(name="token_hash",nullable=false,unique=true,length=64) private String tokenHash; @ManyToOne(optional=false) private User user; @Column(nullable=false) private Instant expiresAt; private Instant usedAt; }

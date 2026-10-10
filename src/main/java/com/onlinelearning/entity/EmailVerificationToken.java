package com.onlinelearning.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;

@Entity
@Table(name = "email_verification_tokens")
@Getter @Setter @NoArgsConstructor
public class EmailVerificationToken extends BaseEntity {
    @Column(nullable = false, unique = true, length = 64)
    private String tokenHash;
    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    private User user;
    @Column(nullable = false)
    private Instant expiresAt;
    private Instant usedAt;
}

package com.onlinelearning.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;

@Entity
@Table(name = "auth_sessions")
@Getter @Setter @NoArgsConstructor
public class AuthSession extends BaseEntity {
    @Column(nullable = false, unique = true, length = 36)
    private String sessionId;
    @ManyToOne(optional = false)
    private User user;
    @Column(nullable = false, length = 64)
    private String refreshTokenHash;
    @Column(nullable = false)
    private Instant expiresAt;
    private Instant revokedAt;

    public boolean isActive(Instant now) {
        return revokedAt == null && expiresAt.isAfter(now);
    }
}

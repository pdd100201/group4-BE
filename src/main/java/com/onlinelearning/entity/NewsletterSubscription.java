package com.onlinelearning.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "newsletter_subscriptions", uniqueConstraints = @UniqueConstraint(columnNames = "email"))
@Getter @Setter @NoArgsConstructor
public class NewsletterSubscription extends BaseEntity {
    @Column(nullable = false, length = 255)
    private String email;

    @Column(nullable = false)
    private Instant subscribedAt;

    @PrePersist
    void onCreate() {
        if (subscribedAt == null) subscribedAt = Instant.now();
    }
}

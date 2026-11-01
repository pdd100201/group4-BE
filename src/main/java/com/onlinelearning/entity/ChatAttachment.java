package com.onlinelearning.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;

@Entity @Table(name = "chat_attachments") @Getter @Setter @NoArgsConstructor
public class ChatAttachment extends BaseEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false) private User owner;
    @ManyToOne(fetch = FetchType.LAZY) private User recipient;
    @Column(nullable = false, length = 36, unique = true) private String storageKey;
    @Column(nullable = false, length = 200) private String originalName;
    @Column(nullable = false, length = 80) private String contentType;
    @Column(nullable = false) private long bytes;
    @Column(nullable = false) private Instant createdAt;
    // Nullable for legacy disk uploads until the resumable migration has completed.
    @Column(length = 255) private String cloudPublicId;
    @Column(length = 10) private String cloudResourceType;
    @Column(length = 20) private String cloudFormat;
    @Column(length = 20) private String cloudDeliveryType;
}

package com.onlinelearning.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;

@Entity @DiscriminatorValue("CHAT_ATTACHMENT")  @Getter @Setter @NoArgsConstructor
public class ChatAttachment extends StoredAsset {
    @ManyToOne(fetch = FetchType.LAZY) private User recipient;
    @Column(length = 36, unique = true) private String storageKey;
    // Nullable for legacy disk uploads until the resumable migration has completed.
    @Column(length = 255) private String cloudPublicId;
    @Column(length = 10) private String cloudResourceType;
    @Column(length = 20) private String cloudFormat;
    @Column(length = 20) private String cloudDeliveryType;
}

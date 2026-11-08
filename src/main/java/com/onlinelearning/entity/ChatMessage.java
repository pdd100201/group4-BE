package com.onlinelearning.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.time.Instant;

@Entity
@Table(name = "chat_messages", indexes = {
    @Index(name = "idx_chat_recipient_id", columnList = "recipient_id,id"),
    @Index(name = "idx_chat_sender_recipient_id", columnList = "sender_id,recipient_id,id"),
    @Index(name = "idx_chat_course_id", columnList = "course_id,id")
}, uniqueConstraints = @UniqueConstraint(name = "uk_chat_sender_request", columnNames = {"sender_id", "client_message_id"}))
@Getter @Setter @NoArgsConstructor
public class ChatMessage extends BaseEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false) private User sender;
    // Legacy community messages have no recipient and remain stored but are no longer exposed.
    @ManyToOne(fetch = FetchType.LAZY) private User recipient;
    @ManyToOne(fetch = FetchType.LAZY) private Course course;
    @Column(nullable = false, length = 36) private String clientMessageId;
    @Column(nullable = false, length = 2000) private String content;
    @Column(nullable = false) private Instant sentAt;
    @OneToOne(fetch = FetchType.LAZY) @JoinColumn(unique = true) private ChatAttachment attachment;
}

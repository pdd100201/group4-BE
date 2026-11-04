package com.onlinelearning.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;

@Entity @Table(name="user_notifications",
 uniqueConstraints=@UniqueConstraint(name="uq_notification_event",columnNames={"recipient_id","event_key"}),
 indexes={@Index(name="idx_notification_inbox",columnList="recipient_id,id"),@Index(name="idx_notification_unread",columnList="recipient_id,read_at")})
@Getter @Setter @NoArgsConstructor
public class UserNotification extends BaseEntity {
 @ManyToOne(optional=false,fetch=FetchType.LAZY) private User recipient;
 @Column(name="event_key",nullable=false,length=180) private String eventKey;
 @Column(nullable=false,length=60) private String type;
 @Column(nullable=false,length=30) private String category;
 @Column(nullable=false,length=200) private String title;
 @Column(nullable=false,length=2000) private String message;
 @Column(nullable=false,length=400) private String link;
 @Column(nullable=false) private Instant createdAt=Instant.now();
 private Instant readAt;
}
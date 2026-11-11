package com.onlinelearning.entity;
import jakarta.persistence.*; import lombok.*; import java.time.Instant;
@Entity @DiscriminatorValue("GENERAL") @Getter @Setter @NoArgsConstructor
public class AuditLog extends ActionLog {   private String targetType; private String targetId; @Column(length=4000) private String metadata;  }

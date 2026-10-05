package com.onlinelearning.entity;
import jakarta.persistence.*; import lombok.*;
@Entity @Getter @Setter @NoArgsConstructor
public class AuditLog extends BaseEntity { @ManyToOne private User actor; @Column(nullable=false) private String action; @Column(nullable=false) private String targetType; private String targetId; @Column(length=4000) private String metadata; }

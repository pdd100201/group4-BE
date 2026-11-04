package com.onlinelearning.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;

@Entity @Table(name="certificate_audit_logs",indexes=@Index(name="idx_certificate_audit",columnList="certificate_id,id"))
@Getter @Setter @NoArgsConstructor
public class CertificateAuditLog extends BaseEntity {
 @ManyToOne(optional=false,fetch=FetchType.LAZY) private CourseCertificate certificate;
 @ManyToOne(optional=false,fetch=FetchType.LAZY) private User actor;
 @Column(nullable=false,length=20) private String action;
 @Column(length=500) private String reason;
 @Column(nullable=false) private Instant occurredAt=Instant.now();
}
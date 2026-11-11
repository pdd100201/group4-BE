package com.onlinelearning.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;

@Entity @DiscriminatorValue("CERTIFICATE") 
@Getter @Setter @NoArgsConstructor
public class CertificateAuditLog extends ActionLog {
 @ManyToOne(fetch=FetchType.LAZY) private CourseCertificate certificate;
 @Column(length=500) private String reason;
}
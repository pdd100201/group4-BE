package com.onlinelearning.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;

@Entity @Table(name="expert_applications", indexes={
    @Index(name="idx_expert_application_applicant",columnList="applicant_id,id"),
    @Index(name="idx_expert_application_status",columnList="status,id")})
@Getter @Setter @NoArgsConstructor
public class ExpertApplication extends BaseEntity {
    @ManyToOne(optional=false,fetch=FetchType.LAZY) private User applicant;
    @Column(nullable=false,length=200) private String specialty;
    @Column(nullable=false) private int experienceYears;
    @Column(nullable=false,length=4000) private String experience;
    @Column(nullable=false,length=4000) private String introduction;
    @Column(length=500) private String portfolioUrl;
    @Enumerated(EnumType.STRING) @Column(nullable=false,length=20)
    private ExpertApplicationStatus status=ExpertApplicationStatus.PENDING;
    @Column(nullable=false,updatable=false) private Instant submittedAt=Instant.now();
    @ManyToOne(fetch=FetchType.LAZY) private User reviewedBy;
    private Instant reviewedAt;
    @Column(length=2000) private String reviewReason;
}

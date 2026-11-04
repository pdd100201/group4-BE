package com.onlinelearning.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;

@Entity @Table(name="course_certificates",
 uniqueConstraints={@UniqueConstraint(name="uq_certificate_student_course",columnNames={"student_id","course_id"}),@UniqueConstraint(name="uq_certificate_code",columnNames="code")},
 indexes=@Index(name="idx_certificate_student",columnList="student_id,id"))
@Getter @Setter @NoArgsConstructor
public class CourseCertificate extends BaseEntity {
 @ManyToOne(optional=false,fetch=FetchType.LAZY) private User student;
 @ManyToOne(optional=false,fetch=FetchType.LAZY) private Course course;
 @ManyToOne(optional=false,fetch=FetchType.LAZY) private CourseRegistration registration;
 @ManyToOne(optional=false,fetch=FetchType.LAZY) private CertificatePolicy policy;
 @Column(nullable=false,length=32) private String code;
 @Column(nullable=false,length=16) private String status="ISSUED";
 @Column(nullable=false,length=255) private String studentNameSnapshot;
 @Column(nullable=false,length=255) private String courseTitleSnapshot;
 @Column(nullable=false,length=255) private String expertNameSnapshot;
 @Column(nullable=false,length=160) private String issuerNameSnapshot;
 @Column(nullable=false) private Instant issuedAt=Instant.now();
 @Lob @Column(nullable=false,columnDefinition="LONGTEXT") private String evidenceSnapshot;
 private Instant revokedAt;
 @Column(length=500) private String revocationReason;
}
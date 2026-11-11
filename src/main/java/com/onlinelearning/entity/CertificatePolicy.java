package com.onlinelearning.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;
import java.util.*;

@Entity @Table(name="certificate_policies",uniqueConstraints=@UniqueConstraint(name="uq_certificate_policy_version",columnNames={"course_id","version"}))
@Getter @Setter @NoArgsConstructor
public class CertificatePolicy extends BaseEntity {
 @ManyToOne(optional=false,fetch=FetchType.LAZY) private Course course;
 @Column(nullable=false) private int version;
 @Column(nullable=false) private boolean enabled;
 @Column(nullable=false,length=160) private String issuerName;
 @Column(nullable=false,length=64) private String contentFingerprint;
 @ManyToOne(optional=false,fetch=FetchType.LAZY) private User configuredBy;
 @Column(nullable=false) private Instant createdAt=Instant.now();
 @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.JSON)
 @Column(columnDefinition="json",nullable=false)
 private Map<Long,String> requiredLessons=new LinkedHashMap<>();
 @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.JSON)
 @Column(columnDefinition="json",nullable=false)
 private Map<Long,String> requiredQuizzes=new LinkedHashMap<>();
}
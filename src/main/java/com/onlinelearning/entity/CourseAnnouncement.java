package com.onlinelearning.entity;
import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;
@Entity @Table(name="course_announcements",uniqueConstraints=@UniqueConstraint(name="uq_announcement_request",columnNames={"course_id","request_key"}))
@Getter @Setter @NoArgsConstructor
public class CourseAnnouncement extends BaseEntity {
 @ManyToOne(optional=false,fetch=FetchType.LAZY) private Course course;
 @Column(name="request_key",nullable=false,length=36) private String requestKey;
 @Column(nullable=false,length=200) private String title;
 @Column(nullable=false,length=1000) private String message;
 @Column(nullable=false) private int recipientCount;
 @Column(nullable=false) private Instant createdAt=Instant.now();
}
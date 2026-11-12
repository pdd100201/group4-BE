package com.onlinelearning.entity;
import jakarta.persistence.*; import lombok.*; import java.math.*; import java.util.*;
@Entity @Getter @Setter @NoArgsConstructor
public class Course extends BaseEntity { @ManyToOne private MediaAsset thumbnail; @Column(nullable=false) private String title; @Column(length=4000) private String description; private String category; private BigDecimal price; @Enumerated(EnumType.STRING) @Column(nullable=false) private CourseStatus status=CourseStatus.DRAFT; @ManyToOne(optional=false) @JoinColumn(name="expert_id") private User expert; @ManyToOne private User reviewedBy; private String rejectionReason; @OneToMany(mappedBy="course") private List<Lesson> lessons=new ArrayList<>();
    @org.hibernate.annotations.CreationTimestamp
    @Column(name="created_at", nullable=false, updatable=false)
    private java.time.Instant createdAt;

    @org.hibernate.annotations.UpdateTimestamp
    @Column(name="updated_at", nullable=false)
    private java.time.Instant updatedAt;

    public record ReviewField(String label, String type, String value) {}

    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.JSON)
    @Column(name="review_baseline", columnDefinition="json")
    private Map<String, ReviewField> reviewBaseline;

    @Column(name="deletion_request_status", nullable=false, length=20)
    private String deletionRequestStatus = "NONE";
    @Column(name="deletion_request_reason", length=1000) private String deletionRequestReason;
    @Column(name="deletion_requested_at") private java.time.Instant deletionRequestedAt;
    @Column(name="deletion_review_reason", length=1000) private String deletionReviewReason;
    @Column(name="deletion_reviewed_at") private java.time.Instant deletionReviewedAt;
    @ManyToOne @JoinColumn(name="deletion_reviewed_by_id") private User deletionReviewedBy;
    @Column(name="deleted_at") private java.time.Instant deletedAt;
    public boolean isDeletionPending() { return "PENDING".equals(deletionRequestStatus); }
    public boolean requiresDeletionApproval() {
        return reviewBaseline != null || status == CourseStatus.PUBLISHED || status == CourseStatus.ARCHIVED || status == CourseStatus.APPROVED;
    }

    public void requireReviewAfterEdit() {
        if (status == CourseStatus.PUBLISHED) {
            status = CourseStatus.DRAFT;
            reviewedBy = null;
            rejectionReason = null;
        }
    }
}

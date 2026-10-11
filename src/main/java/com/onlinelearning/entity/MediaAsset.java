package com.onlinelearning.entity;
import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;
@Entity @Getter @Setter @NoArgsConstructor
public class MediaAsset extends BaseEntity {
    @ManyToOne(optional=false) private User owner;
    @ManyToOne private Course course;
    @Column(nullable=false) private String publicId;
    @Column(nullable=false) private String resourceType;
    @Column(nullable=false) private String deliveryType;
    @Column(nullable=false) private String format;
    @Column(nullable=false, length=1000) private String secureUrl;
    @Column(nullable=false) private String purpose;
    private long bytes;
    private Instant createdAt;
}

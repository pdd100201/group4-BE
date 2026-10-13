package com.onlinelearning.entity;
import jakarta.persistence.*; import lombok.*; import java.util.*;
@Entity @Table(name="questions") @Getter @Setter @NoArgsConstructor
public class Question extends BaseEntity { @ManyToOne(optional=false) private Course course; @Column(nullable=false,length=4000) private String content; private String type; private String explanation; private boolean active=true; @OneToMany(mappedBy="question",cascade=CascadeType.ALL,orphanRemoval=true) private List<QuestionOption> options=new ArrayList<>();
    @Column(name="question_type") private String questionType;
    private String status;
    @ManyToOne private MediaAsset image;
    @org.hibernate.annotations.CreationTimestamp @Column(nullable=false, updatable=false) private java.time.Instant createdAt;
    @org.hibernate.annotations.UpdateTimestamp @Column(nullable=false) private java.time.Instant updatedAt;
}

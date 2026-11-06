package com.onlinelearning.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity @Table(name="expert_application_documents") @Getter @Setter @NoArgsConstructor
public class ExpertApplicationDocument extends BaseEntity {
    @ManyToOne(optional=false,fetch=FetchType.LAZY) private ExpertApplication application;
    @Column(nullable=false,length=200) private String originalName;
    @Column(nullable=false,length=80) private String contentType;
    @Column(nullable=false) private long sizeBytes;
    @Lob @Column(nullable=false,columnDefinition="LONGBLOB") private byte[] content;
}

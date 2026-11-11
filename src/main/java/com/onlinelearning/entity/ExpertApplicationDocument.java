package com.onlinelearning.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity @DiscriminatorValue("EXPERT_DOCUMENT")  @Getter @Setter @NoArgsConstructor
public class ExpertApplicationDocument extends StoredAsset {
    @ManyToOne(fetch=FetchType.LAZY) private ExpertApplication application;
    private long sizeBytes;
    @Lob @Column(columnDefinition="LONGBLOB") private byte[] content;
}

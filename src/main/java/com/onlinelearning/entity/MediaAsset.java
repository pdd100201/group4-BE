package com.onlinelearning.entity;
import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;
@Entity @DiscriminatorValue("MEDIA") @Getter @Setter @NoArgsConstructor
public class MediaAsset extends StoredAsset {
    private String publicId;
    private String resourceType;
    private String deliveryType;
    private String format;
    @Column( length=1000) private String secureUrl;
    private String purpose;
}

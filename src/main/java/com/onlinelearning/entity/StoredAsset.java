package com.onlinelearning.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.Instant;

/** Shared storage; repositories still query each concrete entity independently. */
@Entity
@org.hibernate.annotations.Check(constraints="asset_type in ('MEDIA','CHAT_ATTACHMENT','EXPERT_DOCUMENT') and (asset_type <> 'MEDIA' or (owner_id is not null and public_id is not null and resource_type is not null and delivery_type is not null and format is not null and secure_url is not null and purpose is not null)) and (asset_type <> 'CHAT_ATTACHMENT' or (owner_id is not null and storage_key is not null and original_name is not null and content_type is not null and created_at is not null)) and (asset_type <> 'EXPERT_DOCUMENT' or (application_id is not null and original_name is not null and content_type is not null and size_bytes is not null and content is not null))")
@Table(name="stored_assets", indexes=@Index(name="idx_asset_type_owner",columnList="asset_type,owner_id,id"))
@Inheritance(strategy=InheritanceType.SINGLE_TABLE)
@DiscriminatorColumn(name="asset_type", length=40)
@Getter @Setter
public abstract class StoredAsset extends BaseEntity {

 @ManyToOne(fetch=FetchType.LAZY) private User owner;
 @ManyToOne(fetch=FetchType.LAZY) private Course course;
 @Column(length=200) private String originalName;
 @Column(length=80) private String contentType;
 private long bytes;
 private Instant createdAt;

}

package com.onlinelearning.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.Instant;

/** Shared storage; repositories still query each concrete entity independently. */
@Entity
@org.hibernate.annotations.Check(constraints="log_type in ('GENERAL','CERTIFICATE') and (log_type <> 'GENERAL' or target_type is not null) and (log_type <> 'CERTIFICATE' or (certificate_id is not null and actor_id is not null))")
@Table(name="action_logs", indexes=@Index(name="idx_action_certificate",columnList="log_type,certificate_id,id"))
@Inheritance(strategy=InheritanceType.SINGLE_TABLE)
@DiscriminatorColumn(name="log_type", length=40)
@Getter @Setter
public abstract class ActionLog extends BaseEntity {

 @ManyToOne(fetch=FetchType.LAZY) private User actor;
 @Column(nullable=false,length=255) private String action;
 @Column(nullable=false) private Instant occurredAt=Instant.now();

}

package com.onlinelearning.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.Instant;

/** Shared storage; repositories still query each concrete entity independently. */
@Entity
@org.hibernate.annotations.Check(constraints="token_type in ('EMAIL_VERIFICATION','PASSWORD_RESET')")
@Table(name="auth_tokens", uniqueConstraints=@UniqueConstraint(name="uq_auth_token_type_hash",columnNames={"token_type","token_hash"}), indexes=@Index(name="idx_auth_token_user",columnList="token_type,user_id"))
@Inheritance(strategy=InheritanceType.SINGLE_TABLE)
@DiscriminatorColumn(name="token_type", length=40)
@Getter @Setter
public abstract class AuthToken extends BaseEntity {

 @Column(nullable=false,length=64) private String tokenHash;
 @ManyToOne(optional=false,fetch=FetchType.LAZY) private User user;
 @Column(nullable=false) private Instant expiresAt;
 private Instant usedAt;

}

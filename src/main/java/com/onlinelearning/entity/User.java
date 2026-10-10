package com.onlinelearning.entity;
import jakarta.persistence.*; import lombok.*; import java.time.Instant; import java.util.*;
@Entity @Table(name="users") @Getter @Setter @NoArgsConstructor
public class User extends BaseEntity {
 @Column(nullable=false,unique=true) private String email; @Column(nullable=false) private String passwordHash; @Column(nullable=false) private String fullName;
 @Column(unique=true) private String googleSubject; private String phone; private String avatarUrl;
 @Column(nullable=false) private boolean enabled=false; @Column(nullable=false) private boolean blocked=false;
 @Column(nullable=false) private int failedLoginAttempts=0; private Instant lockedUntil;
 @ManyToMany(fetch=FetchType.EAGER) @JoinTable(name="user_roles",joinColumns=@JoinColumn(name="user_id"),inverseJoinColumns=@JoinColumn(name="role_id")) private Set<Role> roles=new HashSet<>();
}

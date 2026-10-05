package com.onlinelearning.entity;
import jakarta.persistence.*; import lombok.*;
@Entity @Table(name="roles") @Getter @Setter @NoArgsConstructor
public class Role extends BaseEntity { @Column(nullable=false,unique=true) private String code; @Column(nullable=false) private String name; private String description; public String authority(){ return "ROLE_" + code; } }

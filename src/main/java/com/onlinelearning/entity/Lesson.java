package com.onlinelearning.entity;
import jakarta.persistence.*; import lombok.*;
@Entity @Getter @Setter @NoArgsConstructor
public class Lesson extends BaseEntity { @ManyToOne(optional=false) private Course course; @Column(nullable=false) private String title; @Column(length=8000) private String content; private Integer position; private boolean active=true; }

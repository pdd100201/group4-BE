package com.onlinelearning.entity;
import jakarta.persistence.*; import lombok.*; import java.util.*;
@Entity @Table(name="questions") @Getter @Setter @NoArgsConstructor
public class Question extends BaseEntity { @ManyToOne(optional=false) private Course course; @Column(nullable=false,length=4000) private String content; private String type; private String explanation; private boolean active=true; @OneToMany(mappedBy="question",cascade=CascadeType.ALL,orphanRemoval=true) private List<QuestionOption> options=new ArrayList<>(); }

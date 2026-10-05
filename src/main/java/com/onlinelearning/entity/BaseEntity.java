package com.onlinelearning.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@MappedSuperclass @Getter @Setter
public abstract class BaseEntity {
  @Id @GeneratedValue(strategy = GenerationType.IDENTITY) protected Long id;
}

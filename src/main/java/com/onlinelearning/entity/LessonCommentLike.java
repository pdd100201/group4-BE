package com.onlinelearning.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity @Table(name="lesson_comment_likes", uniqueConstraints=
    @UniqueConstraint(name="uq_lesson_comment_like", columnNames={"comment_id","student_id"}))
@Getter @Setter @NoArgsConstructor
public class LessonCommentLike extends BaseEntity {
    @ManyToOne(optional=false, fetch=FetchType.LAZY) private LessonComment comment;
    @ManyToOne(optional=false, fetch=FetchType.LAZY) private User student;
}

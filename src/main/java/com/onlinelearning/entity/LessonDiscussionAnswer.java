package com.onlinelearning.entity;
import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;
@Entity @Table(name="lesson_discussion_answers",indexes=@Index(name="idx_discussion_answer",columnList="discussion_id,id"))
@Getter @Setter @NoArgsConstructor
public class LessonDiscussionAnswer extends BaseEntity {
 @ManyToOne(optional=false,fetch=FetchType.LAZY) private LessonDiscussion discussion;
 @ManyToOne(optional=false,fetch=FetchType.LAZY) private User author;
 @Column(nullable=false,length=4000) private String content;
 private boolean hidden;
 @Column(nullable=false) private Instant createdAt=Instant.now();
}
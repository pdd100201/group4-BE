package com.onlinelearning.entity;
import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;
@Entity @Table(name="lesson_discussions", indexes=@Index(name="idx_discussion_lesson",columnList="lesson_id,id"))
@Getter @Setter @NoArgsConstructor
public class LessonDiscussion extends BaseEntity {
 @ManyToOne(optional=false,fetch=FetchType.LAZY) private Lesson lesson;
 @ManyToOne(optional=false,fetch=FetchType.LAZY) private User author;
 @Column(nullable=false,length=200) private String title;
 @Column(nullable=false,length=4000) private String content;
 private boolean hidden;
 @ManyToOne(fetch=FetchType.LAZY) private LessonDiscussionAnswer acceptedAnswer;
 @Column(nullable=false) private Instant createdAt=Instant.now();
}
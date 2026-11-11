package com.onlinelearning.entity;
import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;
@Entity @DiscriminatorValue("DISCUSSION") 
@Getter @Setter @NoArgsConstructor
public class LessonDiscussion extends DiscussionEntry {
 @Column(length=200) private String title;
 @ManyToOne(fetch=FetchType.LAZY) private LessonDiscussionAnswer acceptedAnswer;
}
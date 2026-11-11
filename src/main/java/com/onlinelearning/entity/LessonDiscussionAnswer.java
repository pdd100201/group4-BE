package com.onlinelearning.entity;
import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;
@Entity @DiscriminatorValue("DISCUSSION_ANSWER") 
@Getter @Setter @NoArgsConstructor
public class LessonDiscussionAnswer extends DiscussionEntry {
 @ManyToOne(fetch=FetchType.LAZY) private LessonDiscussion discussion;
}
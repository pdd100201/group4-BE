package com.onlinelearning.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;

@Entity @DiscriminatorValue("LESSON_COMMENT") 
@Getter @Setter @NoArgsConstructor
public class LessonComment extends DiscussionEntry {
    @ManyToOne(fetch=FetchType.LAZY) private LessonComment parent;
    private boolean deleted;
}

package com.onlinelearning.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.Instant;

/** Shared storage; repositories still query each concrete entity independently. */
@Entity
@org.hibernate.annotations.Check(constraints="entry_type in ('POST_COMMENT','LESSON_COMMENT','DISCUSSION','DISCUSSION_ANSWER') and (entry_type <> 'POST_COMMENT' or post_id is not null) and (entry_type <> 'LESSON_COMMENT' or (lesson_id is not null and deleted is not null)) and (entry_type <> 'DISCUSSION' or (lesson_id is not null and title is not null)) and (entry_type <> 'DISCUSSION_ANSWER' or discussion_id is not null)")
@Table(name="discussion_entries", indexes={@Index(name="idx_entry_lesson",columnList="entry_type,lesson_id,id"),@Index(name="idx_entry_post",columnList="entry_type,post_id,id"),@Index(name="idx_entry_discussion",columnList="entry_type,discussion_id,id"),@Index(name="idx_entry_parent",columnList="entry_type,parent_id,id")})
@Inheritance(strategy=InheritanceType.SINGLE_TABLE)
@DiscriminatorColumn(name="entry_type", length=40)
@Getter @Setter
public abstract class DiscussionEntry extends BaseEntity {

 @ManyToOne(fetch=FetchType.LAZY, optional=false) private User author;
 @ManyToOne(fetch=FetchType.LAZY) private Lesson lesson;
 @Column(nullable=false,length=4000) private String content;
 @Column(nullable=false) private Instant createdAt=Instant.now();
 private Instant updatedAt;
 private boolean hidden;

}

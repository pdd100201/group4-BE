package com.onlinelearning.repository;
import com.onlinelearning.entity.LessonDiscussionAnswer;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;
public interface LessonDiscussionAnswerRepository extends JpaRepository<LessonDiscussionAnswer,Long> {
 Page<LessonDiscussionAnswer> findByDiscussionId(Long id,Pageable page);
 Page<LessonDiscussionAnswer> findByDiscussionIdAndHiddenFalse(Long id,Pageable page);
}
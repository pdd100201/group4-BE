package com.onlinelearning.repository;

import com.onlinelearning.entity.LessonCommentLike;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.*;

public interface LessonCommentLikeRepository extends JpaRepository<LessonCommentLike,Long> {
    Optional<LessonCommentLike> findByCommentIdAndStudentId(Long commentId,Long studentId);
    @Query("select l.comment.id,count(l) from LessonCommentLike l where l.comment.id in :ids group by l.comment.id")
    List<Object[]> likeCounts(@Param("ids") Collection<Long> ids);
    @Query("select l.comment.id from LessonCommentLike l where l.comment.id in :ids and l.student.id=:studentId")
    Set<Long> likedIds(@Param("ids") Collection<Long> ids,@Param("studentId") Long studentId);
}

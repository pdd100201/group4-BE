package com.onlinelearning.repository;

import com.onlinelearning.entity.LessonComment;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.*;

public interface LessonCommentRepository extends JpaRepository<LessonComment,Long> {
    @EntityGraph(attributePaths="author")
    Page<LessonComment> findByLessonIdAndParentIsNullAndDeletedFalse(Long lessonId, Pageable page);
    @EntityGraph(attributePaths="author")
    Page<LessonComment> findByLessonIdAndParentIdAndDeletedFalse(Long lessonId,Long parentId,Pageable page);
    @Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from LessonComment c where c.id=:id and c.lesson.id=:lessonId")
    Optional<LessonComment> findForUpdate(@Param("id") Long id,@Param("lessonId") Long lessonId);
    @Query("select c.parent.id,count(c) from LessonComment c where c.parent.id in :ids and c.deleted=false group by c.parent.id")
    List<Object[]> replyCounts(@Param("ids") Collection<Long> ids);
    @Modifying(flushAutomatically=true,clearAutomatically=true)
    @Query("update LessonComment c set c.deleted=true,c.content='' where c.id=:id or c.parent.id=:id")
    int deleteThread(@Param("id") Long id);
}

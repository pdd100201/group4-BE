package com.onlinelearning.repository;

import com.onlinelearning.entity.*;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;
import java.util.Optional;

public interface PostCommentRepository extends JpaRepository<PostComment, Long>, JpaSpecificationExecutor<PostComment> {
    @EntityGraph(attributePaths="author")
    Page<PostComment> findByPostIdAndParentIdIsNullAndStatus(Long postId,CommentStatus status,Pageable pageable);
    @EntityGraph(attributePaths="author")
    Page<PostComment> findByPostIdAndParentIdAndStatus(Long postId,Long parentId,CommentStatus status,Pageable pageable);
    @Query("select c.parentId,count(c) from PostComment c where c.parentId in :ids and c.status=com.onlinelearning.entity.CommentStatus.VISIBLE group by c.parentId")
    java.util.List<Object[]> replyCounts(@org.springframework.data.repository.query.Param("ids") java.util.Collection<Long> ids);
    @EntityGraph(attributePaths="author")
    Page<PostComment> findByPostIdAndStatus(Long postId, CommentStatus status, Pageable pageable);
    @EntityGraph(attributePaths={"author", "post"})
    Optional<PostComment> findByIdAndPostIdAndStatus(Long id, Long postId, CommentStatus status);
}

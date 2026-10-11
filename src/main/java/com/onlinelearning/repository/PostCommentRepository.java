package com.onlinelearning.repository;

import com.onlinelearning.entity.*;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;
import java.util.Optional;

public interface PostCommentRepository extends JpaRepository<PostComment, Long>, JpaSpecificationExecutor<PostComment> {
    @EntityGraph(attributePaths="author")
    Page<PostComment> findByPostIdAndStatus(Long postId, CommentStatus status, Pageable pageable);
    @EntityGraph(attributePaths={"author", "post"})
    Optional<PostComment> findByIdAndPostIdAndStatus(Long id, Long postId, CommentStatus status);
}

package com.onlinelearning.repository;

import com.onlinelearning.entity.Post;
import com.onlinelearning.entity.PostStatus;
import org.springframework.data.jpa.repository.*;
import java.util.*;

public interface PostRepository extends JpaRepository<Post, Long>, JpaSpecificationExecutor<Post> {
    @EntityGraph(attributePaths="author")
    Optional<Post> findByIdAndPublishedTrueAndDeletedFalseAndStatus(Long id, PostStatus status);

    @Query("select distinct p.category from Post p where p.published=true and p.status=:status and p.deleted=false and p.category is not null and trim(p.category)<>'' order by p.category")
    List<String> publicCategories(@org.springframework.data.repository.query.Param("status") PostStatus status);

    @Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Post p where p.id=:id")
    Optional<Post> findForUpdate(@org.springframework.data.repository.query.Param("id") Long id);
}

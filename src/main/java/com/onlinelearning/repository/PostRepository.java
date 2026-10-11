package com.onlinelearning.repository;

import com.onlinelearning.entity.Post;
import org.springframework.data.jpa.repository.*;
import java.util.*;

public interface PostRepository extends JpaRepository<Post, Long>, JpaSpecificationExecutor<Post> {
    @EntityGraph(attributePaths="author")
    Optional<Post> findByIdAndPublishedTrueAndDeletedFalse(Long id);

    @Query("select distinct p.category from Post p where p.published=true and p.deleted=false and p.category is not null and trim(p.category)<>'' order by p.category")
    List<String> publicCategories();

    @Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Post p where p.id=:id")
    Optional<Post> findForUpdate(@org.springframework.data.repository.query.Param("id") Long id);
}

package com.onlinelearning.repository;
import com.onlinelearning.entity.*; import org.springframework.data.jpa.repository.JpaRepository; import java.util.*;
public interface CourseRepository extends JpaRepository<Course,Long>, org.springframework.data.jpa.repository.JpaSpecificationExecutor<Course> {
 List<Course> findByExpertId(Long expertId);
 @org.springframework.data.jpa.repository.EntityGraph(attributePaths={"expert", "thumbnail"})
 List<Course> findByStatusIn(Collection<CourseStatus> statuses);
 @org.springframework.data.jpa.repository.EntityGraph(attributePaths={"expert", "thumbnail"})
 Optional<Course> findByIdAndStatus(Long id, CourseStatus status);
 @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
 @org.springframework.data.jpa.repository.Query("select c from Course c where c.id = :id")
 Optional<Course> findForUpdate(@org.springframework.data.repository.query.Param("id") Long id);
}

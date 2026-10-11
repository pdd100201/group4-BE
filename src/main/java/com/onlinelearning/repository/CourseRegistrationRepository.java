package com.onlinelearning.repository;

import com.onlinelearning.entity.CourseRegistration;
import java.util.Optional;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.*;

public interface CourseRegistrationRepository extends JpaRepository<CourseRegistration, Long>,
        JpaSpecificationExecutor<CourseRegistration> {
    @Override
    @EntityGraph(attributePaths = {"student", "course"})
    Page<CourseRegistration> findAll(Specification<CourseRegistration> spec, Pageable pageable);

    @Override
    @EntityGraph(attributePaths = {"student", "course"})
    Optional<CourseRegistration> findById(Long id);
}

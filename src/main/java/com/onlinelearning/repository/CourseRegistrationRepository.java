package com.onlinelearning.repository;

import com.onlinelearning.entity.CourseRegistration;
import java.util.Optional;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.*;

public interface CourseRegistrationRepository extends JpaRepository<CourseRegistration, Long>,
        JpaSpecificationExecutor<CourseRegistration> {
    @EntityGraph(attributePaths={"student","course","coursePackage"})
    Optional<CourseRegistration> findByIdAndStudentId(Long id,Long studentId);
    @EntityGraph(attributePaths={"student","course","coursePackage"})
    Optional<CourseRegistration> findFirstByStudentIdAndCourseIdAndStatusOrderByIdDesc(Long studentId,Long courseId,com.onlinelearning.entity.RegistrationStatus status);
    @Query("select count(r) from CourseRegistration r where r.student.id=:studentId and r.course.id=:courseId and r.status=com.onlinelearning.entity.RegistrationStatus.ACTIVE and r.validFrom<=:now and (r.lifetimeSnapshot=true or r.validUntil>:now)")
    long countCurrentAccess(@org.springframework.data.repository.query.Param("studentId") Long studentId,
        @org.springframework.data.repository.query.Param("courseId") Long courseId,@org.springframework.data.repository.query.Param("now") java.time.Instant now);
    @Override
    @EntityGraph(attributePaths = {"student", "course", "coursePackage"})
    Page<CourseRegistration> findAll(Specification<CourseRegistration> spec, Pageable pageable);

    @Override
    @EntityGraph(attributePaths = {"student", "course", "coursePackage"})
    Optional<CourseRegistration> findById(Long id);
}

package com.onlinelearning.repository;
import com.onlinelearning.entity.CourseCertificate;
import org.springframework.data.jpa.repository.*;
import java.util.Optional;
public interface CourseCertificateRepository extends JpaRepository<CourseCertificate,Long>,JpaSpecificationExecutor<CourseCertificate> {
 Optional<CourseCertificate> findByStudentIdAndCourseId(Long studentId,Long courseId);
 @Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
 @Query("select c from CourseCertificate c where c.student.id=:studentId and c.course.id=:courseId")
 Optional<CourseCertificate> findForIssue(@org.springframework.data.repository.query.Param("studentId") Long studentId,@org.springframework.data.repository.query.Param("courseId") Long courseId);
 Optional<CourseCertificate> findByCode(String code);
 boolean existsByCode(String code);
}
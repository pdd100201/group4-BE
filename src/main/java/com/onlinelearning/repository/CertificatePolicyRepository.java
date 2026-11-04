package com.onlinelearning.repository;
import com.onlinelearning.entity.CertificatePolicy;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface CertificatePolicyRepository extends JpaRepository<CertificatePolicy,Long> {
 Optional<CertificatePolicy> findFirstByCourseIdOrderByVersionDesc(Long courseId);
 List<CertificatePolicy> findTop10ByCourseIdOrderByVersionDesc(Long courseId);
}
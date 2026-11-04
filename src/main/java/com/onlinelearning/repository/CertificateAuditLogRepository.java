package com.onlinelearning.repository;
import com.onlinelearning.entity.CertificateAuditLog;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.JpaRepository;
public interface CertificateAuditLogRepository extends JpaRepository<CertificateAuditLog,Long> {
 Page<CertificateAuditLog> findByCertificateIdOrderByIdDesc(Long id,Pageable page);
}
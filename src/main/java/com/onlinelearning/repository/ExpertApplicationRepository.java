package com.onlinelearning.repository;

import com.onlinelearning.entity.ExpertApplication;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.Optional;

public interface ExpertApplicationRepository extends JpaRepository<ExpertApplication,Long>,JpaSpecificationExecutor<ExpertApplication> {
    Optional<ExpertApplication> findFirstByApplicantIdOrderByIdDesc(Long applicantId);
    @Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from ExpertApplication a where a.id=:id")
    Optional<ExpertApplication> findForUpdate(@Param("id") Long id);
}

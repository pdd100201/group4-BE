package com.onlinelearning.repository;

import com.onlinelearning.entity.Transaction;
import java.util.Optional;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.*;

public interface TransactionRepository extends JpaRepository<Transaction, Long>,
        JpaSpecificationExecutor<Transaction> {
    Optional<Transaction> findFirstByPaymentIdAndTypeOrderByIdDesc(Long paymentId, String type);
    Optional<Transaction> findFirstByPaymentIdOrderByIdDesc(Long paymentId);
    @Query("select t.payment.registration.id from Transaction t where t.type='SEPAY_PENDING' "
        + "and t.payment.status=com.onlinelearning.entity.PaymentStatus.PENDING "
        + "and t.payment.provider in ('SEPAY','SEPAY_TEST') and t.createdAt<=:cutoff order by t.id")
    java.util.List<Long> findOverduePaymentRegistrations(@org.springframework.data.repository.query.Param("cutoff") java.time.Instant cutoff, Pageable pageable);
    java.util.List<Transaction> findByPaymentId(Long paymentId);
    @Override
    @EntityGraph(attributePaths = {"payment", "payment.registration", "payment.registration.student",
            "payment.registration.course"})
    Page<Transaction> findAll(Specification<Transaction> spec, Pageable pageable);

    @Override
    @EntityGraph(attributePaths = {"payment", "payment.registration", "payment.registration.student",
            "payment.registration.course"})
    Optional<Transaction> findById(Long id);
}

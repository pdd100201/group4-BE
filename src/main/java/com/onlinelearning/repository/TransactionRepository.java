package com.onlinelearning.repository;

import com.onlinelearning.entity.Transaction;
import java.util.Optional;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.*;

public interface TransactionRepository extends JpaRepository<Transaction, Long>,
        JpaSpecificationExecutor<Transaction> {
    Optional<Transaction> findFirstByPaymentIdAndTypeOrderByIdDesc(Long paymentId, String type);
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

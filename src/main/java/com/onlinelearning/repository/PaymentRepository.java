package com.onlinelearning.repository;

import com.onlinelearning.entity.Payment;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentRepository extends JpaRepository<Payment, Long> {
    Optional<Payment> findByRegistrationId(Long registrationId);
    Optional<Payment> findByProviderReference(String providerReference);
}

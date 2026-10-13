package com.onlinelearning.config;

import com.onlinelearning.repository.TransactionRepository;
import com.onlinelearning.service.PaymentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import java.time.Instant;

@Configuration
@EnableScheduling
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(name="payment.expiry.enabled", havingValue="true", matchIfMissing=true)
public class PaymentExpiryScheduler {
    private final TransactionRepository transactions;
    private final PaymentService payments;

    @Scheduled(fixedDelay=15000, initialDelay=15000)
    public void expirePending() {
        // Each expiry has its own transaction and uses the same locks as payment confirmation.
        for(Long id:transactions.findOverduePaymentRegistrations(Instant.now().minusSeconds(300),PageRequest.of(0,100))) {
            try { payments.expire(id); }
            catch(RuntimeException e) { log.warn("Could not expire payment for registration {}: {}",id,e.getClass().getSimpleName()); }
        }
    }
}

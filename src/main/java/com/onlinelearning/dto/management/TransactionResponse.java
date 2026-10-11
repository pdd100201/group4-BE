package com.onlinelearning.dto.management;

import com.onlinelearning.entity.Transaction;
import com.onlinelearning.entity.PaymentStatus;
import java.math.BigDecimal;
import java.time.Instant;

public record TransactionResponse(Long id, Long paymentId, BigDecimal amount, String type,
        String provider, String providerReference, PaymentStatus status, Instant createdAt,
        RegistrationResponse registration) {
    public static TransactionResponse from(Transaction t) {
        var p = t.getPayment();
        return new TransactionResponse(t.getId(), p.getId(), t.getAmount(), t.getType(),
                p.getProvider(), p.getProviderReference(), p.getStatus(), t.getCreatedAt(),
                RegistrationResponse.from(p.getRegistration()));
    }
}

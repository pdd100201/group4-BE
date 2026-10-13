package com.onlinelearning.dto.payment;

import com.onlinelearning.entity.PaymentStatus;
import java.math.BigDecimal;

public record PaymentResponse(Long registrationId, PaymentStatus status, boolean configured, boolean testMode,
        String qrUrl, String bank, String accountNumber, String accountHolder, String transferContent, BigDecimal amount,
        java.time.Instant expiresAt, java.time.Instant serverTime, boolean expired) {}

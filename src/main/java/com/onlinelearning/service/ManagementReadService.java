package com.onlinelearning.service;

import com.onlinelearning.dto.management.ManagementPage;
import com.onlinelearning.dto.management.RegistrationResponse;
import com.onlinelearning.dto.management.TransactionResponse;
import com.onlinelearning.entity.PaymentStatus;
import com.onlinelearning.entity.RegistrationStatus;
import java.math.BigDecimal;
import java.time.LocalDate;

public interface ManagementReadService {
    ManagementPage<RegistrationResponse> registrations(String q, Long courseId,
            RegistrationStatus status, LocalDate from, LocalDate to, int page, int size);

    RegistrationResponse registration(Long id);

    ManagementPage<TransactionResponse> transactions(String q, Long courseId, PaymentStatus status,
            String provider, LocalDate from, LocalDate to, BigDecimal minAmount, BigDecimal maxAmount,
            int page, int size);

    TransactionResponse transaction(Long id);
}

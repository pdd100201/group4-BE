package com.onlinelearning.dto.dashboard;

import com.onlinelearning.entity.RegistrationStatus;
import java.math.BigDecimal;
import java.time.Instant;

public record DashboardRegistration(Long id, String studentName, String studentEmail,
        String courseTitle, String packageName, BigDecimal amount, String status, Instant createdAt) {
    public DashboardRegistration(Long id, String studentName, String studentEmail,
            String courseTitle, String packageName, BigDecimal amount,
            RegistrationStatus status, Instant createdAt) {
        this(id, studentName, studentEmail, courseTitle, packageName, amount,
                status == null ? null : status.name(), createdAt);
    }
}

package com.onlinelearning.dto.management;

import com.onlinelearning.entity.CourseRegistration;
import com.onlinelearning.entity.RegistrationStatus;
import java.math.BigDecimal;
import java.time.Instant;

public record RegistrationResponse(Long id, Long studentId, String studentName, String studentEmail,
        Long courseId, String courseTitle, RegistrationStatus status, String packageName,
        BigDecimal originalPrice, BigDecimal discount, BigDecimal amount, Integer validityDays,
        Instant createdAt, Instant validFrom, Instant validUntil, String note, Integer validityMonths, Boolean lifetime) {
    public static RegistrationResponse from(CourseRegistration r) {
        return new RegistrationResponse(r.getId(), r.getStudent().getId(), r.getStudent().getFullName(),
                r.getStudent().getEmail(), r.getCourse().getId(), r.getCourse().getTitle(), r.getStatus(),
                r.getPackageNameSnapshot(), r.getPriceSnapshot(), r.getDiscountSnapshot(), r.getAmount(),
                r.getValidityDaysSnapshot(), r.getCreatedAt(), r.getValidFrom(), r.getValidUntil(), r.getNote(),r.getValidityMonthsSnapshot(),r.getLifetimeSnapshot());
    }
}

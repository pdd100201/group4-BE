package com.onlinelearning.dto.registration;

import com.onlinelearning.entity.*;
import java.math.BigDecimal;
import java.time.Instant;

public record StudentRegistrationResponse(Long id,Long courseId,String courseTitle,Long packageId,String packageName,
        PackageDuration packageDuration,Integer durationMonths,boolean lifetime,BigDecimal originalPrice,
        BigDecimal discountPercent,BigDecimal discountAmount,BigDecimal amount,String currency,
        RegistrationStatus status,Instant createdAt,Instant validFrom,Instant validUntil,
        String fullName,String email,String phone,boolean reused,boolean paymentPending) {
    public static StudentRegistrationResponse from(CourseRegistration r, boolean reused) {
        return new StudentRegistrationResponse(r.getId(),r.getCourse().getId(),r.getCourse().getTitle(),
            r.getCoursePackage()==null?null:r.getCoursePackage().getId(),r.getPackageNameSnapshot(),r.getPackageDurationSnapshot(),
            r.getValidityMonthsSnapshot(),Boolean.TRUE.equals(r.getLifetimeSnapshot()),r.getPriceSnapshot(),
            r.getDiscountPercentSnapshot(),r.getDiscountSnapshot(),r.getAmount(),"VND",r.getStatus(),r.getCreatedAt(),
            r.getValidFrom(),r.getValidUntil(),r.getStudentNameSnapshot()==null?r.getStudent().getFullName():r.getStudentNameSnapshot(),
            r.getStudentEmailSnapshot()==null?r.getStudent().getEmail():r.getStudentEmailSnapshot(),r.getStudentPhoneSnapshot(),
            reused,r.getStatus()==RegistrationStatus.PENDING_PAYMENT);
    }
}

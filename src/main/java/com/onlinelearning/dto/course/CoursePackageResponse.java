package com.onlinelearning.dto.course;

import com.onlinelearning.entity.*;
import java.math.*;

public record CoursePackageResponse(Long id, PackageDuration duration, String name, Integer durationMonths,
        boolean lifetime, BigDecimal price, BigDecimal discountPercent, BigDecimal discountAmount,
        BigDecimal finalPrice, boolean active) {
    public static CoursePackageResponse from(CoursePackage p) {
        BigDecimal finalPrice = p.getPrice().multiply(BigDecimal.ONE.subtract(p.getDiscountPercent().movePointLeft(2)))
            .setScale(0, RoundingMode.HALF_UP);
        return new CoursePackageResponse(p.getId(), p.getDuration(), p.getDuration().label(), p.getDuration().months(),
            p.getDuration() == PackageDuration.LIFETIME, p.getPrice(), p.getDiscountPercent(),
            p.getPrice().subtract(finalPrice), finalPrice, p.isActive());
    }
}

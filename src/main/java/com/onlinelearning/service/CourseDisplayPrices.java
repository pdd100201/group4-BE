package com.onlinelearning.service;

import com.onlinelearning.dto.course.CoursePackageResponse;
import com.onlinelearning.entity.CoursePackage;
import java.math.BigDecimal;
import java.util.Collection;
import java.util.Map;
import java.util.stream.Collectors;

/** Same active-package, discount and rounding rules as the public catalogue. */
public final class CourseDisplayPrices {
    private CourseDisplayPrices() {}
    public static Map<Long, BigDecimal> from(Collection<CoursePackage> packages) {
        return packages.stream().filter(CoursePackage::isActive).collect(Collectors.toMap(
            p -> p.getCourse().getId(), p -> CoursePackageResponse.from(p).finalPrice(), BigDecimal::min));
    }
}

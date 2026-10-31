package com.onlinelearning.service;

import com.onlinelearning.entity.*;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CourseDisplayPricesTest {
    private CoursePackage pack(long courseId, String price, String discount, boolean active) {
        Course course = new Course(); course.setId(courseId);
        CoursePackage p = new CoursePackage(); p.setCourse(course); p.setDuration(PackageDuration.ONE_MONTH);
        p.setPrice(new BigDecimal(price)); p.setDiscountPercent(new BigDecimal(discount)); p.setActive(active);
        return p;
    }
    @Test void minimumUsesDiscountedActivePackagesAndKeepsCoursesSeparate() {
        var prices = CourseDisplayPrices.from(List.of(pack(1,"100000","0",true), pack(1,"200000","75",true),
            pack(1,"1","0",false), pack(2,"900000","10",true)));
        assertEquals(new BigDecimal("50000"), prices.get(1L));
        assertEquals(new BigDecimal("810000"), prices.get(2L));
    }
    @Test void noActivePackageIsMissingRatherThanFree() {
        assertTrue(CourseDisplayPrices.from(List.of(pack(1,"100000","0",false))).isEmpty());
    }
    @Test void zeroAndRoundingFollowPublicPackageRules() {
        var prices = CourseDisplayPrices.from(List.of(pack(1,"100001","50",true),pack(2,"300000","100",true)));
        assertEquals(new BigDecimal("50001"), prices.get(1L));
        assertEquals(BigDecimal.ZERO, prices.get(2L));
    }
}

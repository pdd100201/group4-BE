package com.onlinelearning.service;

import com.onlinelearning.entity.*;
import com.onlinelearning.repository.*;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
import java.math.BigDecimal;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ManagedCoursePriceTest {
    @Test void expertAndAdminListsUseActivePackagePriceInsteadOfLegacyCoursePrice() {
        CourseRepository courses = mock(CourseRepository.class);
        UserRepository users = mock(UserRepository.class);
        CoursePackageRepository packages = mock(CoursePackageRepository.class);
        User expert = new User(); expert.setId(2L); expert.setFullName("Expert");
        Course course = new Course(); course.setId(1L); course.setExpert(expert); course.setPrice(new BigDecimal("999"));
        CoursePackage pack = new CoursePackage(); pack.setCourse(course); pack.setDuration(PackageDuration.ONE_MONTH);
        pack.setPrice(new BigDecimal("200000")); pack.setDiscountPercent(new BigDecimal("25"));
        when(users.findByEmailIgnoreCase("expert@test.local")).thenReturn(Optional.of(expert));
        when(courses.findAll(any(Specification.class), any(Pageable.class))).thenReturn(new PageImpl<>(List.of(course)));
        when(packages.findByCourseIdInAndActiveTrue(List.of(1L))).thenReturn(List.of(pack));
        var expertService = new ExpertCourseService(courses, users, mock(AuditLogRepository.class), mock(EntityManager.class), mock(MediaService.class), packages, mock(CourseReviewChangesService.class));
        var expertResult = expertService.list("expert@test.local", "", "", null, 0, 10).getContent().getFirst();
        assertEquals(new BigDecimal("150000"), expertResult.displayPrice());
        assertEquals(new BigDecimal("999"), expertResult.price(), "Editing must retain the legacy field without overwriting package configuration");
        var manager = new ManagerCourseService(courses, mock(EntityManager.class), packages);
        assertEquals(new BigDecimal("150000"), manager.list("", "", null, 0, 10).getContent().getFirst().price());
        when(packages.findByCourseIdInAndActiveTrue(List.of(1L))).thenReturn(List.of());
        assertNull(expertService.list("expert@test.local", "", "", null, 0, 10).getContent().getFirst().displayPrice());
        assertNull(manager.list("", "", null, 0, 10).getContent().getFirst().price());
    }
}

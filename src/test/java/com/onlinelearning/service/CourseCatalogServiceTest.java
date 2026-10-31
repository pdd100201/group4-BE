package com.onlinelearning.service;

import com.onlinelearning.entity.*;
import com.onlinelearning.exception.ApiException;
import com.onlinelearning.repository.*;
import com.onlinelearning.service.impl.CourseCatalogServiceImpl;
import jakarta.persistence.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import java.math.BigDecimal;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Tracking RMS rows 15-16: UC10/UC11. SQL predicates have separate integration coverage. */
@ExtendWith(MockitoExtension.class)
class CourseCatalogServiceTest {
    @Mock CourseRepository courses;
    @Mock EntityManager em;
    @Mock CoursePackageRepository packages;
    @InjectMocks CourseCatalogServiceImpl service;

    private Course course() {
        User expert = new User(); expert.setFullName("Expert");
        Course course = new Course(); course.setId(1L); course.setExpert(expert);
        course.setTitle("Java"); course.setPrice(new BigDecimal("999999"));
        course.setStatus(CourseStatus.PUBLISHED); return course;
    }

    private CoursePackage price(Course course, long id, String amount, String discount) {
        CoursePackage p = new CoursePackage(); p.setId(id); p.setCourse(course);
        p.setDuration(PackageDuration.ONE_MONTH); p.setPrice(new BigDecimal(amount));
        p.setDiscountPercent(new BigDecimal(discount)); return p;
    }

    @Test
    void uc10EmptyCatalogDoesNotQueryPackages() {
        when(courses.findAll(any(Specification.class), any(Sort.class))).thenReturn(List.of());
        assertTrue(service.listPublished().isEmpty()); verifyNoInteractions(packages, em);
    }

    @Test
    void uc10ShowsLowestDiscountedActivePackagePriceInsteadOfLegacyPrice() {
        Course course = course();
        when(courses.findAll(any(Specification.class), any(Sort.class))).thenReturn(List.of(course));
        when(packages.findByCourseIdInAndActiveTrue(List.of(1L))).thenReturn(List.of(
                price(course, 1, "200000", "50"), price(course, 2, "150000", "0")));
        var result = service.listPublished("Java", "Programming");
        assertEquals(1, result.size());
        assertEquals(0, new BigDecimal("100000").compareTo(result.getFirst().price()));
        assertEquals("Expert", result.getFirst().expertName());
    }

    @Test
    void uc10CourseWithoutAvailablePackagesHasNoRegistrationPrice() {
        when(courses.findAll(any(Specification.class), any(Sort.class))).thenReturn(List.of(course()));
        when(packages.findByCourseIdInAndActiveTrue(List.of(1L))).thenReturn(List.of());
        assertNull(service.listPublished().getFirst().price());
    }

    @Test
    void uc10RejectsOversizedSearchAndCategoryBeforeQuerying() {
        assertEquals(HttpStatus.BAD_REQUEST, assertThrows(ApiException.class,
                () -> service.listPublished("a".repeat(256), "")).status());
        assertEquals(HttpStatus.BAD_REQUEST, assertThrows(ApiException.class,
                () -> service.listPublished("", "a".repeat(256))).status());
        verifyNoInteractions(courses, packages, em);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(longs={0,-1})
    void uc11RejectsInvalidCourseId(Long id) {
        assertEquals(HttpStatus.BAD_REQUEST, assertThrows(ApiException.class,
                () -> service.getPublished(id)).status());
        verifyNoInteractions(courses, packages, em);
    }

    @Test
    void uc11NonPublicOrMissingCourseReturns404() {
        when(courses.findByIdAndStatus(1L, CourseStatus.PUBLISHED)).thenReturn(Optional.empty());
        assertEquals(HttpStatus.NOT_FOUND, assertThrows(ApiException.class,
                () -> service.getPublished(1L)).status());
        verifyNoInteractions(packages, em);
    }

    @Test
    void uc11ReturnsOutlineAndActivePackagesWithLowestDiscountedPrice() {
        Course course = course();
        when(courses.findByIdAndStatus(1L, CourseStatus.PUBLISHED)).thenReturn(Optional.of(course));
        TypedQuery<Tuple> lessons = mock(TypedQuery.class);
        when(em.createQuery(anyString(), eq(Tuple.class))).thenReturn(lessons);
        when(lessons.setParameter("id", 1L)).thenReturn(lessons);
        Tuple row = mock(Tuple.class);
        when(row.get("id", Long.class)).thenReturn(10L);
        when(row.get("title", String.class)).thenReturn("Introduction");
        when(row.get("position", Integer.class)).thenReturn(1);
        when(lessons.getResultList()).thenReturn(List.of(row));
        TypedQuery<Long> quizzes = mock(TypedQuery.class);
        when(em.createQuery(anyString(), eq(Long.class))).thenReturn(quizzes);
        when(quizzes.setParameter("id", 1L)).thenReturn(quizzes);
        when(quizzes.getSingleResult()).thenReturn(2L);
        when(packages.findByCourseIdAndActiveTrueOrderByIdAsc(1L)).thenReturn(List.of(
                price(course, 1, "100000", "10"), price(course, 2, "200000", "0")));

        var result = service.getPublished(1L);

        assertEquals(2, result.packages().size());
        assertEquals(0, new BigDecimal("90000").compareTo(result.price()));
        assertEquals(2, result.quizCount());
        assertEquals("Introduction", result.lessons().getFirst().title());
        assertNull(result.thumbnailUrl());
    }
}

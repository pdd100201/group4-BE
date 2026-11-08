package com.onlinelearning.service.RegistrationsTest;

import com.onlinelearning.dto.management.RegistrationResponse;
import com.onlinelearning.entity.*;
import com.onlinelearning.exception.ApiException;
import com.onlinelearning.repository.CourseRegistrationRepository;
import com.onlinelearning.repository.TransactionRepository;
import com.onlinelearning.service.impl.ManagementReadServiceImpl;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RegistrationTest {
    @Mock CourseRegistrationRepository registrations;
    @Mock TransactionRepository transactions;
    @InjectMocks ManagementReadServiceImpl service;

    @AfterEach
    void clearAuthentication() {
        SecurityContextHolder.clearContext();
    }

    private void authenticate(String role) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("user@example.test", "unused",
                        List.of(new SimpleGrantedAuthority("ROLE_" + role))));
    }

    @ParameterizedTest
    @ValueSource(strings = {"MANAGER", "ADMIN"})
    void detailReturnsStoredSnapshotsWithoutRecomputingCurrentPrice(String role) {
        authenticate(role);
        User student = new User();
        student.setId(1L);
        student.setFullName("Test Student");
        student.setEmail("student@example.test");
        Course course = new Course();
        course.setId(2L);
        course.setTitle("Java Course");
        course.setPrice(new BigDecimal("999000"));
        CourseRegistration registration = new CourseRegistration();
        registration.setId(3L);
        registration.setStudent(student);
        registration.setCourse(course);
        registration.setStatus(RegistrationStatus.ACTIVE);
        registration.setPackageNameSnapshot("Original package");
        registration.setPriceSnapshot(new BigDecimal("100000"));
        registration.setDiscountSnapshot(new BigDecimal("20000"));
        registration.setAmount(new BigDecimal("80000"));
        registration.setValidityDaysSnapshot(30);
        registration.setValidityMonthsSnapshot(1);
        registration.setLifetimeSnapshot(false);
        registration.setCreatedAt(Instant.parse("2026-10-01T00:00:00Z"));
        registration.setValidFrom(Instant.parse("2026-10-02T00:00:00Z"));
        registration.setValidUntil(Instant.parse("2026-11-02T00:00:00Z"));
        registration.setNote("Original registration note");
        when(registrations.findById(3L)).thenReturn(Optional.of(registration));

        RegistrationResponse result = service.registration(3L);

        assertAll(
                () -> assertEquals(3L, result.id()),
                () -> assertEquals(1L, result.studentId()),
                () -> assertEquals("Test Student", result.studentName()),
                () -> assertEquals("student@example.test", result.studentEmail()),
                () -> assertEquals(2L, result.courseId()),
                () -> assertEquals("Java Course", result.courseTitle()),
                () -> assertEquals(RegistrationStatus.ACTIVE, result.status()),
                () -> assertEquals("Original package", result.packageName()),
                () -> assertEquals(new BigDecimal("100000"), result.originalPrice()),
                () -> assertEquals(new BigDecimal("20000"), result.discount()),
                () -> assertEquals(new BigDecimal("80000"), result.amount()),
                () -> assertEquals(30, result.validityDays()),
                () -> assertEquals(1, result.validityMonths()),
                () -> assertEquals(Boolean.FALSE, result.lifetime()),
                () -> assertEquals(registration.getCreatedAt(), result.createdAt()),
                () -> assertEquals(registration.getValidFrom(), result.validFrom()),
                () -> assertEquals(registration.getValidUntil(), result.validUntil()),
                () -> assertEquals(registration.getNote(), result.note()));
        verify(registrations).findById(3L);
        verifyNoInteractions(transactions);
    }

    @Test
    void missingRegistrationReturnsNotFound() {
        authenticate("MANAGER");

        ApiException error = assertThrows(ApiException.class, () -> service.registration(99L));

        assertEquals(HttpStatus.NOT_FOUND, error.status());
        verify(registrations).findById(99L);
    }

    @ParameterizedTest
    @ValueSource(strings = {"STUDENT", "EXPERT"})
    void nonManagementRoleCannotReadDetail(String role) {
        authenticate(role);

        ApiException error = assertThrows(ApiException.class, () -> service.registration(3L));

        assertEquals(HttpStatus.FORBIDDEN, error.status());
        verifyNoInteractions(registrations, transactions);
    }

    @Test
    void missingAuthenticationIsRejected() {
        SecurityContextHolder.clearContext();

        ApiException error = assertThrows(ApiException.class, () -> service.registration(3L));

        assertEquals(HttpStatus.UNAUTHORIZED, error.status());
        verifyNoInteractions(registrations, transactions);
    }
}

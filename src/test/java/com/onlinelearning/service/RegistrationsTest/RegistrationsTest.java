package com.onlinelearning.service.RegistrationsTest;

import com.onlinelearning.dto.management.ManagementPage;
import com.onlinelearning.dto.management.RegistrationResponse;
import com.onlinelearning.entity.*;
import com.onlinelearning.exception.ApiException;
import com.onlinelearning.repository.CourseRegistrationRepository;
import com.onlinelearning.repository.TransactionRepository;
import com.onlinelearning.service.impl.ManagementReadServiceImpl;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RegistrationsTest {
    @Mock CourseRegistrationRepository registrations;
    @Mock TransactionRepository transactions;
    @InjectMocks ManagementReadServiceImpl service;

    @BeforeEach
    void authenticateManager() {
        authenticate("MANAGER");
    }

    @AfterEach
    void clearAuthentication() {
        SecurityContextHolder.clearContext();
    }

    private void authenticate(String role) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("manager@example.test", "unused",
                        List.of(new SimpleGrantedAuthority("ROLE_" + role))));
    }

    private ManagementPage<RegistrationResponse> list(int page, int size) {
        return service.registrations(null, null, null, null, null, page, size);
    }

    private CourseRegistration registration() {
        User student = new User();
        student.setId(1L);
        student.setFullName("Test Student");
        student.setEmail("student@example.test");
        Course course = new Course();
        course.setId(2L);
        course.setTitle("Java Course");
        CourseRegistration result = new CourseRegistration();
        result.setId(3L);
        result.setStudent(student);
        result.setCourse(course);
        result.setAmount(new BigDecimal("80000"));
        result.setPackageNameSnapshot("Original package");
        result.setCreatedAt(Instant.parse("2026-10-01T12:00:00Z"));
        return result;
    }

    @ParameterizedTest
    @ValueSource(strings = {"MANAGER", "ADMIN"})
    void authorizedRolesReceiveMappedPageAndDescendingIdSort(String role) {
        authenticate(role);
        CourseRegistration registration = registration();
        when(registrations.findAll(org.mockito.ArgumentMatchers.<Specification<CourseRegistration>>any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(registration), PageRequest.of(1, 2), 5));

        ManagementPage<RegistrationResponse> result = list(1, 2);

        assertEquals(1, result.page());
        assertEquals(2, result.size());
        assertEquals(5, result.totalElements());
        assertEquals(3, result.totalPages());
        RegistrationResponse item = result.items().getFirst();
        assertEquals(3L, item.id());
        assertEquals(1L, item.studentId());
        assertEquals("Test Student", item.studentName());
        assertEquals("student@example.test", item.studentEmail());
        assertEquals(2L, item.courseId());
        assertEquals("Java Course", item.courseTitle());
        assertEquals("Original package", item.packageName());
        assertEquals(new BigDecimal("80000"), item.amount());
        assertEquals(registration.getCreatedAt(), item.createdAt());
        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(registrations).findAll(org.mockito.ArgumentMatchers.<Specification<CourseRegistration>>any(), pageable.capture());
        assertEquals(PageRequest.of(1, 2, Sort.by(Sort.Direction.DESC, "id")), pageable.getValue());
        verifyNoInteractions(transactions);
    }

    @Test
    void noMatchingRegistrationsReturnEmptyPage() {
        when(registrations.findAll(org.mockito.ArgumentMatchers.<Specification<CourseRegistration>>any(), any(Pageable.class)))
                .thenReturn(Page.empty(PageRequest.of(0, 20)));

        ManagementPage<RegistrationResponse> result = list(0, 20);

        assertTrue(result.items().isEmpty());
        assertEquals(0, result.totalElements());
        assertEquals(0, result.totalPages());
    }

    static Stream<Arguments> invalidFilters() {
        LocalDate day = LocalDate.of(2026, 10, 1);
        return Stream.of(
                Arguments.of(null, null, null, null, -1, 20),
                Arguments.of(null, null, null, null, 0, 0),
                Arguments.of(null, null, null, null, 0, 101),
                Arguments.of("x".repeat(201), null, null, null, 0, 20),
                Arguments.of(null, 0L, null, null, 0, 20),
                Arguments.of(null, -1L, null, null, 0, 20),
                Arguments.of(null, null, day.plusDays(1), day, 0, 20),
                Arguments.of(null, null, null, LocalDate.MAX, 0, 20));
    }

    @ParameterizedTest
    @MethodSource("invalidFilters")
    void invalidInputIsRejectedBeforeQuery(String q, Long courseId, LocalDate from,
            LocalDate to, int page, int size) {
        ApiException error = assertThrows(ApiException.class,
                () -> service.registrations(q, courseId, null, from, to, page, size));

        assertEquals(HttpStatus.BAD_REQUEST, error.status());
        verifyNoInteractions(registrations, transactions);
    }

    @Test
    void maximumAllowedSearchAndPageSizeAreAccepted() {
        LocalDate day = LocalDate.of(2026, 10, 1);
        when(registrations.findAll(org.mockito.ArgumentMatchers.<Specification<CourseRegistration>>any(), any(Pageable.class)))
                .thenReturn(Page.empty(PageRequest.of(0, 100)));

        assertDoesNotThrow(() -> service.registrations("x".repeat(200), 1L,
                RegistrationStatus.ACTIVE, day, day, 0, 100));

        verify(registrations).findAll(org.mockito.ArgumentMatchers.<Specification<CourseRegistration>>any(), any(Pageable.class));
    }

    @ParameterizedTest
    @ValueSource(strings = {"STUDENT", "EXPERT"})
    void nonManagementRoleCannotReadRegistrations(String role) {
        authenticate(role);

        ApiException error = assertThrows(ApiException.class, () -> list(0, 20));

        assertEquals(HttpStatus.FORBIDDEN, error.status());
        verifyNoInteractions(registrations, transactions);
    }

    @Test
    void missingAuthenticationIsRejected() {
        SecurityContextHolder.clearContext();

        ApiException error = assertThrows(ApiException.class, () -> list(0, 20));

        assertEquals(HttpStatus.UNAUTHORIZED, error.status());
        verifyNoInteractions(registrations, transactions);
    }

    @Test
    void unauthenticatedTokenIsRejected() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("manager@example.test", "unused"));

        ApiException error = assertThrows(ApiException.class, () -> list(0, 20));

        assertEquals(HttpStatus.UNAUTHORIZED, error.status());
        verifyNoInteractions(registrations, transactions);
    }
}

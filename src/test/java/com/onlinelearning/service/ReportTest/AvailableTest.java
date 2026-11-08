package com.onlinelearning.service.ReportTest;

import com.onlinelearning.dto.report.ReportKind;
import com.onlinelearning.exception.ApiException;
import com.onlinelearning.repository.ReportRepository;
import com.onlinelearning.service.impl.ReportServiceImpl;
import java.util.List;
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
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class AvailableTest {
    @Mock ReportRepository reports;
    @InjectMocks ReportServiceImpl service;

    @AfterEach
    void clearAuthentication() {
        SecurityContextHolder.clearContext();
    }

    private void authenticate(String role) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("user@example.test", "unused",
                        List.of(new SimpleGrantedAuthority("ROLE_" + role))));
    }

    @Test
    void adminReceivesAllThreeReportKindsWithoutDatabaseQuery() {
        authenticate("ADMIN");

        List<ReportKind> result = service.available();

        assertEquals(List.of(ReportKind.COURSES, ReportKind.REGISTRATIONS, ReportKind.REVENUE), result);
        verifyNoInteractions(reports);
    }

    @ParameterizedTest
    @ValueSource(strings = {"MANAGER", "STUDENT", "EXPERT"})
    void nonAdminRolesCannotListReports(String role) {
        authenticate(role);

        ApiException error = assertThrows(ApiException.class, () -> service.available());

        assertEquals(HttpStatus.FORBIDDEN, error.status());
        verifyNoInteractions(reports);
    }

    @Test
    void missingAuthenticationIsRejected() {
        SecurityContextHolder.clearContext();

        ApiException error = assertThrows(ApiException.class, () -> service.available());

        assertEquals(HttpStatus.UNAUTHORIZED, error.status());
        verifyNoInteractions(reports);
    }

    @Test
    void unauthenticatedTokenIsRejected() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("admin@example.test", "unused"));

        ApiException error = assertThrows(ApiException.class, () -> service.available());

        assertEquals(HttpStatus.UNAUTHORIZED, error.status());
        verifyNoInteractions(reports);
    }
}

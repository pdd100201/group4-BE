package com.onlinelearning.service.ReportTest;

import com.onlinelearning.dto.report.*;
import com.onlinelearning.entity.RegistrationStatus;
import com.onlinelearning.exception.ApiException;
import com.onlinelearning.repository.ReportRepository;
import com.onlinelearning.service.impl.ReportServiceImpl;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
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
class ReadTest {
    @Mock ReportRepository reports;
    @InjectMocks ReportServiceImpl service;

    @BeforeEach
    void authenticateAdmin() {
        authenticate("ADMIN");
    }

    @AfterEach
    void clearAuthentication() {
        SecurityContextHolder.clearContext();
    }

    private void authenticate(String role) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("user@example.test", "unused",
                        List.of(new SimpleGrantedAuthority("ROLE_" + role))));
    }

    private ReportResponse readDefaultReport() {
        return service.read(ReportKind.REVENUE, null, null, null, null, 0, 20);
    }

    @ParameterizedTest
    @EnumSource(ReportKind.class)
    void returnsRepositoryRowsAndTotalWithRequestedPagination(ReportKind kind) {
        ReportRow row = new ReportRow(2L, "Java Course", 3L, new BigDecimal("250000"));
        BigDecimal total = new BigDecimal("900000");
        when(reports.read(kind, 2L, null, null, null, 20, 20))
                .thenReturn(new ReportRepository.Result(List.of(row), 41, total));

        ReportResponse result = service.read(kind, 2L, null, null, null, 1, 20);

        assertEquals(kind, result.kind());
        assertEquals(total, result.totalAmount());
        assertEquals(List.of(row), result.records().items());
        assertEquals(1, result.records().page());
        assertEquals(20, result.records().size());
        assertEquals(41, result.records().totalElements());
        assertEquals(3, result.records().totalPages());
        verify(reports).read(kind, 2L, null, null, null, 20, 20);
    }

    @Test
    void datesAreConvertedToUtcWithExclusiveNextDayEnd() {
        LocalDate from = LocalDate.of(2026, 10, 1);
        LocalDate to = LocalDate.of(2026, 10, 31);
        Instant start = Instant.parse("2026-10-01T00:00:00Z");
        Instant end = Instant.parse("2026-11-01T00:00:00Z");
        when(reports.read(ReportKind.REVENUE, null, null, start, end, 0, 20))
                .thenReturn(new ReportRepository.Result(List.of(), 0, BigDecimal.ZERO));

        service.read(ReportKind.REVENUE, null, null, from, to, 0, 20);

        verify(reports).read(ReportKind.REVENUE, null, null, start, end, 0, 20);
    }

    @Test
    void registrationReportPassesActiveStatusFilterToRepository() {
        RegistrationStatus status = RegistrationStatus.ACTIVE;
        when(reports.read(ReportKind.REGISTRATIONS, 1L, status, null, null, 0, 20))
                .thenReturn(new ReportRepository.Result(List.of(), 0, BigDecimal.ZERO));

        ReportResponse result = service.read(ReportKind.REGISTRATIONS, 1L, status,
                null, null, 0, 20);

        assertEquals(ReportKind.REGISTRATIONS, result.kind());
        verify(reports).read(ReportKind.REGISTRATIONS, 1L, status, null, null, 0, 20);
    }

    @Test
    void emptyReportReturnsZeroTotalsAndNoRecords() {
        when(reports.read(ReportKind.REVENUE, null, null, null, null, 0, 20))
                .thenReturn(new ReportRepository.Result(List.of(), 0, BigDecimal.ZERO));

        ReportResponse result = readDefaultReport();

        assertTrue(result.records().items().isEmpty());
        assertEquals(0, result.records().totalElements());
        assertEquals(0, result.records().totalPages());
        assertEquals(BigDecimal.ZERO, result.totalAmount());
    }

    @Test
    void pageSizeAboveMaximumIsRejectedBeforeRepositoryQuery() {
        ApiException error = assertThrows(ApiException.class,
                () -> service.read(ReportKind.REVENUE, null, null, null, null, 0, 101));

        assertEquals(HttpStatus.BAD_REQUEST, error.status());
        verifyNoInteractions(reports);
    }

    @Test
    void statusFilterIsRejectedForRevenueReport() {
        ApiException error = assertThrows(ApiException.class,
                () -> service.read(ReportKind.REVENUE, null, RegistrationStatus.ACTIVE,
                        null, null, 0, 20));

        assertEquals(HttpStatus.BAD_REQUEST, error.status());
        verifyNoInteractions(reports);
    }

    @Test
    void managerCannotReadAdminReports() {
        authenticate("MANAGER");

        ApiException error = assertThrows(ApiException.class, this::readDefaultReport);

        assertEquals(HttpStatus.FORBIDDEN, error.status());
        verifyNoInteractions(reports);
    }

    @Test
    void missingAuthenticationIsRejected() {
        SecurityContextHolder.clearContext();

        ApiException error = assertThrows(ApiException.class, this::readDefaultReport);

        assertEquals(HttpStatus.UNAUTHORIZED, error.status());
        verifyNoInteractions(reports);
    }

}

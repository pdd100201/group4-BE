package com.onlinelearning.service.ReportTest;

import com.onlinelearning.dto.report.*;
import com.onlinelearning.entity.CourseStatus;
import com.onlinelearning.entity.RegistrationStatus;
import com.onlinelearning.exception.ApiException;
import com.onlinelearning.repository.ReportRepository;
import com.onlinelearning.service.impl.ReportServiceImpl;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ExportTest {
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

    private byte[] export(ReportKind kind) {
        return service.export(kind, null, null, null, null);
    }

    private void stubSingleRow(ReportKind kind, ReportRow row) {
        when(reports.read(kind, null, null, null, null, 0, 100))
                .thenReturn(new ReportRepository.Result(List.of(row), 1, row.amount()));
    }

    @ParameterizedTest
    @CsvSource({
            "COURSES,Registrations",
            "REVENUE,Successful payments",
            "REGISTRATIONS,Registration ID"
    })
    void emptyReportStillHasCorrectCsvHeaderAndUtf8Bom(ReportKind kind, String headerLabel) {
        when(reports.read(kind, null, null, null, null, 0, 100))
                .thenReturn(new ReportRepository.Result(List.of(), 0, BigDecimal.ZERO));

        byte[] result = export(kind);
        String csv = new String(result, StandardCharsets.UTF_8);

        assertArrayEquals(new byte[]{(byte) 0xEF, (byte) 0xBB, (byte) 0xBF},
                java.util.Arrays.copyOf(result, 3));
        assertTrue(csv.contains("\"" + headerLabel + "\""));
        assertTrue(csv.endsWith("\r\n"));
        assertEquals(1, csv.lines().count());
        if (kind == ReportKind.REGISTRATIONS) {
            assertEquals("\uFEFF\"Registration ID\",\"Course\",\"Student\",\"Email\",\"Status\","
                    + "\"Created at (UTC)\",\"Amount (VND)\",\"Demo\"\r\n", csv);
        } else {
            assertEquals("\uFEFF\"Course ID\",\"Course\",\"Status\",\"" + headerLabel
                    + "\",\"Revenue (VND)\",\"Demo\"\r\n", csv);
        }
    }

    @Test
    void registrationCsvContainsStudentStatusDateAndAmount() {
        ReportRow row = new ReportRow(7L, "Java Course", RegistrationStatus.ACTIVE,
                new BigDecimal("80000"), "Test Student", "student@example.test",
                Instant.parse("2026-10-01T12:00:00Z"), "OLS-DEMO-7");
        stubSingleRow(ReportKind.REGISTRATIONS, row);

        String csv = new String(export(ReportKind.REGISTRATIONS), StandardCharsets.UTF_8);

        assertTrue(csv.endsWith("\"7\",\"Java Course\",\"Test Student\",\"student@example.test\","
                + "\"ACTIVE\",\"2026-10-01T12:00:00Z\",\"80000\",\"true\"\r\n"));
    }

    @Test
    void courseCsvContainsCourseStatusRegistrationCountAndRevenue() {
        ReportRow row = new ReportRow(2L, "Java Course", CourseStatus.PUBLISHED,
                3L, new BigDecimal("240000"));
        stubSingleRow(ReportKind.COURSES, row);

        String csv = new String(export(ReportKind.COURSES), StandardCharsets.UTF_8);

        assertTrue(csv.endsWith("\"2\",\"Java Course\",\"PUBLISHED\",\"3\",\"240000\",\"false\"\r\n"));
    }

    @Test
    void revenueCsvHasSuccessfulPaymentCountAndEmptyStatus() {
        stubSingleRow(ReportKind.REVENUE,
                new ReportRow(2L, "Java Course", 3L, new BigDecimal("240000")));

        String csv = new String(export(ReportKind.REVENUE), StandardCharsets.UTF_8);

        assertTrue(csv.endsWith("\"2\",\"Java Course\",\"\",\"3\",\"240000\",\"false\"\r\n"));
    }

    @Test
    void multiplePagesAreExportedWithoutRepeatingOrSkippingRows() {
        when(reports.read(eq(ReportKind.REVENUE), isNull(), isNull(), isNull(), isNull(),
                anyInt(), eq(100))).thenAnswer(invocation -> {
                    int offset = invocation.getArgument(5);
                    List<ReportRow> rows = IntStream.range(offset, Math.min(offset + 100, 201))
                            .mapToObj(i -> new ReportRow((long) i + 1, "Course " + (i + 1),
                                    1L, BigDecimal.TEN)).toList();
                    return new ReportRepository.Result(rows, 201, new BigDecimal("2010"));
                });

        String csv = new String(export(ReportKind.REVENUE), StandardCharsets.UTF_8);
        List<String> lines = csv.lines().toList();

        assertEquals(202, lines.size());
        for (int id = 1; id <= 201; id++) {
            assertEquals("\"" + id + "\",\"Course " + id + "\",\"\",\"1\",\"10\",\"false\"",
                    lines.get(id));
        }
        verify(reports).read(ReportKind.REVENUE, null, null, null, null, 0, 100);
        verify(reports).read(ReportKind.REVENUE, null, null, null, null, 100, 100);
        verify(reports).read(ReportKind.REVENUE, null, null, null, null, 200, 100);
        verifyNoMoreInteractions(reports);
    }

    @Test
    void exactlyTenThousandRowsCanBeExported() {
        when(reports.read(eq(ReportKind.REVENUE), isNull(), isNull(), isNull(), isNull(),
                anyInt(), eq(100))).thenAnswer(invocation -> {
                    int offset = invocation.getArgument(5);
                    List<ReportRow> rows = IntStream.range(offset, offset + 100)
                            .mapToObj(i -> new ReportRow((long) i + 1, "Course " + (i + 1),
                                    1L, BigDecimal.ONE)).toList();
                    return new ReportRepository.Result(rows, 10000, new BigDecimal("10000"));
                });

        String csv = new String(export(ReportKind.REVENUE), StandardCharsets.UTF_8);

        assertEquals(10001, csv.lines().count());
        assertTrue(csv.endsWith("\"10000\",\"Course 10000\",\"\",\"1\",\"1\",\"false\"\r\n"));
        verify(reports, times(100)).read(eq(ReportKind.REVENUE), isNull(), isNull(),
                isNull(), isNull(), anyInt(), eq(100));
    }

    @Test
    void moreThanTenThousandRowsAreRejectedBeforeFetchingMorePages() {
        when(reports.read(ReportKind.REVENUE, null, null, null, null, 0, 100))
                .thenReturn(new ReportRepository.Result(List.of(), 10001, BigDecimal.ZERO));

        ApiException error = assertThrows(ApiException.class, () -> export(ReportKind.REVENUE));

        assertEquals(HttpStatus.BAD_REQUEST, error.status());
        verify(reports).read(ReportKind.REVENUE, null, null, null, null, 0, 100);
        verifyNoMoreInteractions(reports);
    }

    @Test
    void commaQuotesNewlineAndVietnameseTextArePreservedInQuotedCell() {
        String title = "Java, \"C\u01a1 b\u1ea3n\"\nAdvanced";
        stubSingleRow(ReportKind.REVENUE, new ReportRow(1L, title, 1L, BigDecimal.TEN));

        String csv = new String(export(ReportKind.REVENUE), StandardCharsets.UTF_8);

        assertTrue(csv.contains("\"Java, \"\"C\u01a1 b\u1ea3n\"\"\nAdvanced\""));
    }

    @ParameterizedTest
    @ValueSource(strings = {"=SUM(A1:A2)", "+1+1", "-1+1", "@SUM(A1:A2)", "  =1+1", "\t+1+1"})
    void formulaLikeTextIsPrefixedWithoutChangingNumericAmounts(String title) {
        stubSingleRow(ReportKind.REVENUE, new ReportRow(1L, title, 1L, new BigDecimal("-10")));

        String csv = new String(export(ReportKind.REVENUE), StandardCharsets.UTF_8);

        assertTrue(csv.contains("\"'" + title + "\""));
        assertTrue(csv.contains("\"-10\""));
        assertFalse(csv.contains("\"'-10\""));
    }

    @Test
    void nullCellsAreExportedAsEmptyStrings() {
        ReportRow row = new ReportRow(1L, null, null, null, null, null, null, null, false);
        stubSingleRow(ReportKind.REVENUE, row);

        String csv = new String(export(ReportKind.REVENUE), StandardCharsets.UTF_8);

        assertTrue(csv.endsWith("\"1\",\"\",\"\",\"\",\"\",\"false\"\r\n"));
        assertFalse(csv.contains("\"null\""));
    }

    @Test
    void registrationFiltersAreKeptForEveryExportedPage() {
        LocalDate from = LocalDate.of(2026, 10, 1);
        LocalDate to = LocalDate.of(2026, 10, 31);
        Instant start = Instant.parse("2026-10-01T00:00:00Z");
        Instant end = Instant.parse("2026-11-01T00:00:00Z");
        ReportRow row = new ReportRow(1L, "Java Course", RegistrationStatus.ACTIVE,
                BigDecimal.TEN, "Student", "student@example.test", start, null);
        when(reports.read(eq(ReportKind.REGISTRATIONS), eq(2L), eq(RegistrationStatus.ACTIVE),
                eq(start), eq(end), anyInt(), eq(100)))
                .thenReturn(new ReportRepository.Result(List.of(row), 101, BigDecimal.TEN));

        service.export(ReportKind.REGISTRATIONS, 2L, RegistrationStatus.ACTIVE, from, to);

        verify(reports).read(ReportKind.REGISTRATIONS, 2L, RegistrationStatus.ACTIVE, start, end, 0, 100);
        verify(reports).read(ReportKind.REGISTRATIONS, 2L, RegistrationStatus.ACTIVE, start, end, 100, 100);
        verifyNoMoreInteractions(reports);
    }

    @ParameterizedTest
    @EnumSource(value = ReportKind.class, names = {"COURSES", "REVENUE"})
    void statusFilterIsRejectedForNonRegistrationExports(ReportKind kind) {
        ApiException error = assertThrows(ApiException.class, () -> service.export(kind,
                null, RegistrationStatus.ACTIVE, null, null));

        assertEquals(HttpStatus.BAD_REQUEST, error.status());
        verifyNoInteractions(reports);
    }

    @ParameterizedTest
    @ValueSource(strings = {"MANAGER", "STUDENT", "EXPERT"})
    void nonAdminRolesCannotExportReports(String role) {
        authenticate(role);

        ApiException error = assertThrows(ApiException.class, () -> export(ReportKind.REVENUE));

        assertEquals(HttpStatus.FORBIDDEN, error.status());
        verifyNoInteractions(reports);
    }

    @Test
    void missingAuthenticationIsRejected() {
        SecurityContextHolder.clearContext();

        ApiException error = assertThrows(ApiException.class, () -> export(ReportKind.REVENUE));

        assertEquals(HttpStatus.UNAUTHORIZED, error.status());
        verifyNoInteractions(reports);
    }
}

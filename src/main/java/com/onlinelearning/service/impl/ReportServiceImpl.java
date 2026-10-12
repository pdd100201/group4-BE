package com.onlinelearning.service.impl;

import com.onlinelearning.dto.management.ManagementPage;
import com.onlinelearning.dto.report.*;
import com.onlinelearning.entity.RegistrationStatus;
import com.onlinelearning.exception.ApiException;
import com.onlinelearning.repository.ReportRepository;
import com.onlinelearning.service.ReportService;
import java.time.*;
import java.util.*;
import java.nio.charset.StandardCharsets;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReportServiceImpl implements ReportService {
    private final ReportRepository reports;

    @Override
    public List<ReportKind> available() { authorize(); return List.of(ReportKind.values()); }

    @Override
    public ReportResponse read(ReportKind kind, Long courseId, RegistrationStatus status,
            LocalDate from, LocalDate to, int page, int size) {
        authorize();
        validate(kind, courseId, status, from, to, page, size);
        var result = reports.read(kind, courseId, status,
                from == null ? null : from.atStartOfDay().toInstant(ZoneOffset.UTC),
                to == null ? null : to.plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC),
                Math.multiplyExact(page, size), size);
        var records = new ManagementPage<>(result.rows(), page, size, result.count(),
                (int) ((result.count() + size - 1) / size));
        return new ReportResponse(kind, records, result.amount());
    }

    @Override
    public byte[] export(ReportKind kind, Long courseId, RegistrationStatus status, LocalDate from, LocalDate to) {
        var first = read(kind, courseId, status, from, to, 0, 100);
        if (first.records().totalElements() > 10000)
            throw new ApiException(HttpStatus.BAD_REQUEST, "Export limit is 10000 rows; narrow the filters");
        StringBuilder csv = new StringBuilder("\uFEFF");
        append(csv, kind == ReportKind.REGISTRATIONS
                ? List.of("Registration ID", "Course", "Student", "Email", "Status", "Created at (UTC)", "Amount (VND)", "Demo")
                : List.of("Course ID", "Course", "Status", kind == ReportKind.COURSES ? "Registrations" : "Successful payments", "Revenue (VND)", "Demo"));
        for (int page = 0; page < first.records().totalPages(); page++) {
            var data = page == 0 ? first : read(kind, courseId, status, from, to, page, 100);
            for (var row : data.records().items()) {
                if (kind == ReportKind.REGISTRATIONS)
                    append(csv, Arrays.asList(row.id(), row.courseTitle(), row.studentName(), row.studentEmail(),
                            row.status(), row.createdAt(), row.amount(), row.demo()));
                else append(csv, Arrays.asList(row.id(), row.courseTitle(), row.status(), row.count(), row.amount(), row.demo()));
            }
        }
        return csv.toString().getBytes(StandardCharsets.UTF_8);
    }

    private void append(StringBuilder csv, List<?> cells) {
        var values = cells.stream().map(value -> {
            String text = value == null ? "" : value.toString();
            // Neutralize spreadsheet formulas without altering numeric values.
            String leading = text.stripLeading();
            if (value instanceof String && !leading.isEmpty() && "=+@-".indexOf(leading.charAt(0)) >= 0)
                text = "'" + text;
            return "\"" + text.replace("\"", "\"\"") + "\"";
        }).toList();
        csv.append(String.join(",", values)).append("\r\n");
    }

    private void validate(ReportKind kind, Long courseId, RegistrationStatus status,
            LocalDate from, LocalDate to, int page, int size) {
        if (kind == null || page < 0 || size < 1 || size > 100 || page > Integer.MAX_VALUE / size
                || (courseId != null && courseId < 1) || LocalDate.MAX.equals(to)
                || (from != null && to != null && from.isAfter(to))
                || (status != null && kind != ReportKind.REGISTRATIONS))
            throw new ApiException(HttpStatus.BAD_REQUEST, "Invalid report filters");
    }

    private void authorize() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated())
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Authentication required");
        if (auth.getAuthorities().stream().noneMatch(a -> a.getAuthority().equals("ROLE_ADMIN")))
            throw new ApiException(HttpStatus.FORBIDDEN, "Admin report access required");
    }
}

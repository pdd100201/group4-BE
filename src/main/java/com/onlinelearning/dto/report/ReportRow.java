package com.onlinelearning.dto.report;
import java.math.BigDecimal;
import java.time.Instant;
import com.onlinelearning.entity.CourseStatus;
import com.onlinelearning.entity.RegistrationStatus;
public record ReportRow(Long id, String courseTitle, String status, Long count,
        BigDecimal amount, String studentName, String studentEmail, Instant createdAt, boolean demo, Long lessonCount, Long questionCount) {
    public ReportRow(Long id, String title, CourseStatus status, Long lessons, Long questions) {
        this(id, title, status.name(), null, null, null, null, null, title.startsWith("[DEMO"), lessons, questions);
    }
    public ReportRow(Long id, String title, CourseStatus status, Long count, BigDecimal amount) {
        this(id, title, status.name(), count, amount, null, null, null, title.startsWith("[DEMO"), null, null);
    }
    public ReportRow(Long id, String title, Long count, BigDecimal amount) {
        this(id, title, null, count, amount, null, null, null, title.startsWith("[DEMO"), null, null);
    }
    public ReportRow(Long id, String title, RegistrationStatus status, BigDecimal amount,
            String student, String email, Instant createdAt, String note) {
        this(id, title, status == null ? null : status.name(), null, amount, student, email,
                createdAt, note != null && note.startsWith("OLS-DEMO-"), null, null);
    }
}

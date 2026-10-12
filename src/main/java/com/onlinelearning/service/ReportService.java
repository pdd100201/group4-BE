package com.onlinelearning.service;
import com.onlinelearning.dto.report.*;
import com.onlinelearning.entity.RegistrationStatus;
import java.time.LocalDate;
import java.util.List;
public interface ReportService {
    List<ReportKind> available();
    ReportResponse read(ReportKind kind, Long courseId, RegistrationStatus status,
            LocalDate from, LocalDate to, int page, int size);
    byte[] export(ReportKind kind, Long courseId, RegistrationStatus status, LocalDate from, LocalDate to);
}

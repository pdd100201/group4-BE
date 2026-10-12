package com.onlinelearning.dto.report;
import com.onlinelearning.dto.management.ManagementPage;
import java.math.BigDecimal;
public record ReportResponse(ReportKind kind, ManagementPage<ReportRow> records, BigDecimal totalAmount) {}

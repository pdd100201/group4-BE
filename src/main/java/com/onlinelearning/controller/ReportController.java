package com.onlinelearning.controller;

import com.onlinelearning.dto.report.*;
import com.onlinelearning.entity.RegistrationStatus;
import com.onlinelearning.service.ReportService;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/reports")
@RequiredArgsConstructor
public class ReportController {
    private final ReportService service;
    @GetMapping public List<ReportKind> available() { return service.available(); }

    @GetMapping("/{kind}")
    public ReportResponse read(@PathVariable ReportKind kind,
            @RequestParam(required=false) Long courseId, @RequestParam(required=false) RegistrationStatus status,
            @RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue="0") int page, @RequestParam(defaultValue="20") int size) {
        return service.read(kind, courseId, status, from, to, page, size);
    }

    @GetMapping("/{kind}/export")
    public ResponseEntity<byte[]> export(@PathVariable ReportKind kind,
            @RequestParam(required=false) Long courseId, @RequestParam(required=false) RegistrationStatus status,
            @RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate to) {
        return ResponseEntity.ok().contentType(MediaType.parseMediaType("text/csv;charset=UTF-8"))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"report-" + kind.name().toLowerCase() + ".csv\"")
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(service.export(kind, courseId, status, from, to));
    }
}

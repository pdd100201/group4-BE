package com.onlinelearning.controller;

import com.onlinelearning.dto.management.*;
import com.onlinelearning.entity.*;
import com.onlinelearning.service.ManagementReadService;
import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping({"/api/v1/admin", "/api/v1/manager"})
@RequiredArgsConstructor
public class ManagementReadController {
    private final ManagementReadService service;

    @GetMapping("/registrations")
    public ManagementPage<RegistrationResponse> registrations(
            @RequestParam(required = false) String q, @RequestParam(required = false) Long courseId,
            @RequestParam(required = false) RegistrationStatus status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return service.registrations(q, courseId, status, from, to, page, size);
    }

    @GetMapping("/registrations/{id}")
    public RegistrationResponse registration(@PathVariable Long id) {
        return service.registration(id);
    }

    @GetMapping("/transactions")
    public ManagementPage<TransactionResponse> transactions(
            @RequestParam(required = false) String q, @RequestParam(required = false) Long courseId,
            @RequestParam(required = false) PaymentStatus status, @RequestParam(required = false) String provider,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) BigDecimal minAmount, @RequestParam(required = false) BigDecimal maxAmount,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return service.transactions(q, courseId, status, provider, from, to, minAmount, maxAmount, page, size);
    }

    @GetMapping("/transactions/{id}")
    public TransactionResponse transaction(@PathVariable Long id) {
        return service.transaction(id);
    }
}

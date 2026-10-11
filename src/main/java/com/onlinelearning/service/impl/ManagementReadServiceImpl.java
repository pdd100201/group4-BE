package com.onlinelearning.service.impl;

import com.onlinelearning.dto.management.*;
import com.onlinelearning.entity.*;
import com.onlinelearning.exception.ApiException;
import com.onlinelearning.repository.*;
import com.onlinelearning.service.ManagementReadService;
import jakarta.persistence.criteria.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ManagementReadServiceImpl implements ManagementReadService {
    private final CourseRegistrationRepository registrations;
    private final TransactionRepository transactions;

    @Override
    public ManagementPage<RegistrationResponse> registrations(String q, Long courseId,
            RegistrationStatus status, LocalDate from, LocalDate to, int page, int size) {
        authorize();
        validate(from, to, page, size);
        var result = registrations.findAll((root, query, cb) -> {
            List<Predicate> filters = new ArrayList<>();
            registrationFilters(root, cb, filters, q, courseId);
            if (status != null) filters.add(cb.equal(root.get("status"), status));
            dateFilters(root, cb, filters, from, to);
            return cb.and(filters.toArray(Predicate[]::new));
        }, PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "id")));
        return ManagementPage.from(result.map(RegistrationResponse::from));
    }

    @Override
    public RegistrationResponse registration(Long id) {
        authorize();
        return RegistrationResponse.from(registrations.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Registration not found")));
    }

    @Override
    public ManagementPage<TransactionResponse> transactions(String q, Long courseId, PaymentStatus status,
            String provider, LocalDate from, LocalDate to, BigDecimal minAmount, BigDecimal maxAmount,
            int page, int size) {
        authorize();
        validate(from, to, page, size);
        if ((minAmount != null && minAmount.signum() < 0) ||
                (maxAmount != null && maxAmount.signum() < 0) ||
                (minAmount != null && maxAmount != null && minAmount.compareTo(maxAmount) > 0)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Invalid amount range");
        }
        var result = transactions.findAll((root, query, cb) -> {
            List<Predicate> filters = new ArrayList<>();
            var payment = root.join("payment");
            var registration = payment.join("registration");
            registrationFilters(registration, cb, filters, null, courseId);
            if (q != null && !q.isBlank()) {
                String pattern = pattern(q);
                filters.add(cb.or(
                        cb.like(cb.lower(payment.get("providerReference")), pattern, '\\'),
                        cb.like(cb.lower(registration.join("student").get("email")), pattern, '\\'),
                        cb.like(cb.lower(registration.join("student").get("fullName")), pattern, '\\'),
                        cb.like(cb.lower(registration.join("course").get("title")), pattern, '\\')));
            }
            if (status != null) filters.add(cb.equal(payment.get("status"), status));
            if (provider != null && !provider.isBlank())
                filters.add(cb.equal(cb.lower(payment.get("provider")), provider.trim().toLowerCase(Locale.ROOT)));
            if (minAmount != null) filters.add(cb.greaterThanOrEqualTo(root.get("amount"), minAmount));
            if (maxAmount != null) filters.add(cb.lessThanOrEqualTo(root.get("amount"), maxAmount));
            dateFilters(root, cb, filters, from, to);
            return cb.and(filters.toArray(Predicate[]::new));
        }, PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "id")));
        return ManagementPage.from(result.map(TransactionResponse::from));
    }

    @Override
    public TransactionResponse transaction(Long id) {
        authorize();
        return TransactionResponse.from(transactions.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Transaction not found")));
    }

    private void authorize() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated())
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Authentication required");
        if (auth.getAuthorities().stream().noneMatch(a ->
                a.getAuthority().equals("ROLE_MANAGER") || a.getAuthority().equals("ROLE_ADMIN")))
            throw new ApiException(HttpStatus.FORBIDDEN, "Management access required");
    }

    private void validate(LocalDate from, LocalDate to, int page, int size) {
        if (page < 0 || size < 1 || size > 100 || (from != null && to != null && from.isAfter(to)) ||
                LocalDate.MAX.equals(to))
            throw new ApiException(HttpStatus.BAD_REQUEST, "Invalid pagination or date range");
    }

    private void registrationFilters(From<?, ?> root, CriteriaBuilder cb, List<Predicate> filters,
            String q, Long courseId) {
        if (courseId != null) filters.add(cb.equal(root.get("course").get("id"), courseId));
        if (q != null && !q.isBlank()) {
            String pattern = pattern(q);
            var student = root.join("student");
            filters.add(cb.or(cb.like(cb.lower(student.get("fullName")), pattern, '\\'),
                    cb.like(cb.lower(student.get("email")), pattern, '\\'),
                    cb.like(cb.lower(root.join("course").get("title")), pattern, '\\')));
        }
    }

    private void dateFilters(From<?, ?> root, CriteriaBuilder cb, List<Predicate> filters,
            LocalDate from, LocalDate to) {
        // Date filters use UTC, with an exclusive end boundary to include the whole last day.
        if (from != null) filters.add(cb.greaterThanOrEqualTo(root.get("createdAt"),
                from.atStartOfDay().toInstant(ZoneOffset.UTC)));
        if (to != null) filters.add(cb.lessThan(root.get("createdAt"),
                to.plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC)));
    }

    private String pattern(String q) {
        if (q.length() > 200) throw new ApiException(HttpStatus.BAD_REQUEST, "Search is too long");
        return "%" + q.trim().toLowerCase(Locale.ROOT).replace("\\", "\\\\")
                .replace("%", "\\%").replace("_", "\\_") + "%";
    }
}

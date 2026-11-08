package com.onlinelearning.service.TransactionsTest;

import com.onlinelearning.common.TransactionOutcome;
import com.onlinelearning.dto.management.ManagementPage;
import com.onlinelearning.dto.management.TransactionResponse;
import com.onlinelearning.entity.*;
import com.onlinelearning.exception.ApiException;
import com.onlinelearning.repository.CourseRegistrationRepository;
import com.onlinelearning.repository.TransactionRepository;
import com.onlinelearning.service.impl.ManagementReadServiceImpl;
import jakarta.persistence.criteria.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TransactionsTest {
    @Mock CourseRegistrationRepository registrations;
    @Mock TransactionRepository transactions;
    @InjectMocks ManagementReadServiceImpl service;
    @Mock Root<Transaction> root;
    @Mock CriteriaQuery<?> query;
    @Mock CriteriaBuilder cb;
    @Mock Join<Transaction, Payment> paymentJoin;
    @Mock Join<Payment, CourseRegistration> registrationJoin;
    @Mock Path<BigDecimal> amountPath;
    @Mock Path<Instant> createdAtPath;
    @Mock Path<String> typePath;
    @Mock Path<String> providerPath;
    @Mock Expression<String> lowerProvider;
    @Mock Path<PaymentStatus> paymentStatusPath;
    @Mock Predicate legacyProvider;
    @Mock Predicate providerMatches;

    @BeforeEach
    void authenticateManager() {
        authenticate("MANAGER");
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

    private ManagementPage<TransactionResponse> list() {
        return service.transactions(null, null, null, null, null, null, null, null, 0, 20);
    }

    private Transaction transaction() {
        User student = new User();
        student.setId(1L);
        student.setFullName("Test Student");
        student.setEmail("student@example.test");
        Course course = new Course();
        course.setId(2L);
        course.setTitle("Java Course");
        CourseRegistration registration = new CourseRegistration();
        registration.setId(3L);
        registration.setStudent(student);
        registration.setCourse(course);
        Payment payment = new Payment();
        payment.setId(4L);
        payment.setRegistration(registration);
        payment.setProvider("SEPAY_TEST");
        payment.setProviderReference("SEPAY_TEST:123");
        payment.setStatus(PaymentStatus.SUCCEEDED);
        Transaction result = new Transaction();
        result.setId(5L);
        result.setPayment(payment);
        result.setAmount(new BigDecimal("100000"));
        result.setType("SEPAY_SUCCEEDED");
        result.setCreatedAt(Instant.parse("2026-10-01T23:59:59Z"));
        return result;
    }

    private void executeSpecificationWhenRepositoryIsCalled() {
        when(root.<Transaction, Payment>join("payment")).thenReturn(paymentJoin);
        when(paymentJoin.<Payment, CourseRegistration>join("registration")).thenReturn(registrationJoin);
        when(transactions.findAll(org.mockito.ArgumentMatchers.<Specification<Transaction>>any(), any(Pageable.class)))
                .thenAnswer(invocation -> {
                    Specification<Transaction> spec = invocation.getArgument(0);
                    spec.toPredicate(root, query, cb);
                    return Page.empty(invocation.getArgument(1, Pageable.class));
                });
    }

    @ParameterizedTest
    @ValueSource(strings = {"MANAGER", "ADMIN"})
    void authorizedRolesReceiveMappedPageAndDescendingSort(String role) {
        authenticate(role);
        when(transactions.findAll(org.mockito.ArgumentMatchers.<Specification<Transaction>>any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(transaction()), PageRequest.of(1, 2), 5));

        ManagementPage<TransactionResponse> result = service.transactions(null, null, null,
                null, null, null, null, null, 1, 2);

        assertEquals(1, result.page());
        assertEquals(2, result.size());
        assertEquals(5, result.totalElements());
        assertEquals(3, result.totalPages());
        TransactionResponse item = result.items().getFirst();
        assertEquals(5L, item.id());
        assertEquals(4L, item.paymentId());
        assertEquals(new BigDecimal("100000"), item.amount());
        assertEquals(PaymentStatus.SUCCEEDED, item.status());
        assertEquals("SEPAY_TEST", item.provider());
        assertEquals("SEPAY_TEST:123", item.providerReference());
        assertEquals(3L, item.registration().id());
        assertEquals("student@example.test", item.registration().studentEmail());
        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(transactions).findAll(org.mockito.ArgumentMatchers.<Specification<Transaction>>any(), pageable.capture());
        assertEquals(PageRequest.of(1, 2, Sort.by(Sort.Direction.DESC, "id")), pageable.getValue());
        verifyNoInteractions(registrations);
    }

    @Test
    void noMatchingTransactionsReturnEmptyPage() {
        when(transactions.findAll(org.mockito.ArgumentMatchers.<Specification<Transaction>>any(), any(Pageable.class)))
                .thenReturn(Page.empty(PageRequest.of(0, 20)));

        ManagementPage<TransactionResponse> result = list();

        assertTrue(result.items().isEmpty());
        assertEquals(0, result.totalElements());
        assertEquals(0, result.totalPages());
    }

    @Test
    void negativeMinimumAmountIsRejectedBeforeQuery() {
        ApiException error = assertThrows(ApiException.class, () -> service.transactions(
                null, null, null, null, null, null, new BigDecimal("-1"), null, 0, 20));

        assertEquals(HttpStatus.BAD_REQUEST, error.status());
        verifyNoInteractions(transactions, registrations);
    }

    @Test
    void pageSizeAboveMaximumIsRejectedBeforeQuery() {
        ApiException error = assertThrows(ApiException.class, () -> service.transactions(
                null, null, null, null, null, null, null, null, 0, 101));

        assertEquals(HttpStatus.BAD_REQUEST, error.status());
        verifyNoInteractions(transactions, registrations);
    }

    @Test
    void amountFiltersUseInclusiveLowerAndUpperBounds() {
        executeSpecificationWhenRepositoryIsCalled();
        when(root.<BigDecimal>get("amount")).thenReturn(amountPath);
        BigDecimal min = new BigDecimal("100.00");
        BigDecimal max = new BigDecimal("200.00");

        service.transactions(null, null, null, null, null, null, min, max, 0, 20);

        verify(cb).greaterThanOrEqualTo(amountPath, min);
        verify(cb).lessThanOrEqualTo(amountPath, max);
    }

    @Test
    void dateFiltersIncludeEntireLastDayInUtc() {
        executeSpecificationWhenRepositoryIsCalled();
        when(root.<Instant>get("createdAt")).thenReturn(createdAtPath);
        LocalDate day = LocalDate.of(2026, 10, 1);

        service.transactions(null, null, null, null, day, day, null, null, 0, 20);

        verify(cb).greaterThanOrEqualTo(createdAtPath, Instant.parse("2026-10-01T00:00:00Z"));
        verify(cb).lessThan(createdAtPath, Instant.parse("2026-10-02T00:00:00Z"));
    }

    @Test
    void providerFilterNormalizesVnpayAndIncludesLegacyTransactions() {
        String provider = "vnpay";
        executeSpecificationWhenRepositoryIsCalled();
        when(root.<String>get("type")).thenReturn(typePath);
        when(paymentJoin.<String>get("provider")).thenReturn(providerPath);
        when(cb.lower(providerPath)).thenReturn(lowerProvider);
        when(cb.like(typePath, "VNPAY\\_%", '\\')).thenReturn(legacyProvider);
        when(cb.equal(lowerProvider, provider)).thenReturn(providerMatches);

        service.transactions(null, null, null, " " + provider + " ",
                null, null, null, null, 0, 20);

        verify(cb).equal(lowerProvider, provider);
        verify(cb).like(typePath, "VNPAY\\_%", '\\');
        verify(cb).or(legacyProvider, providerMatches);
    }

    @Test
    void succeededStatusFilterUsesTransactionTypesAndLegacyPaymentFallback() {
        PaymentStatus status = PaymentStatus.SUCCEEDED;
        executeSpecificationWhenRepositoryIsCalled();
        when(root.<String>get("type")).thenReturn(typePath);
        when(paymentJoin.<PaymentStatus>get("status")).thenReturn(paymentStatusPath);

        service.transactions(null, null, status, null, null, null, null, null, 0, 20);

        verify(typePath).in(TransactionOutcome.knownTypes());
        verify(cb).equal(paymentStatusPath, status);
        verify(typePath).in(TransactionOutcome.typesFor(status));
    }

    @Test
    void studentCannotReadTransactions() {
        authenticate("STUDENT");

        ApiException error = assertThrows(ApiException.class, this::list);

        assertEquals(HttpStatus.FORBIDDEN, error.status());
        verifyNoInteractions(transactions, registrations);
    }

    @Test
    void missingAuthenticationIsRejected() {
        SecurityContextHolder.clearContext();

        ApiException error = assertThrows(ApiException.class, this::list);

        assertEquals(HttpStatus.UNAUTHORIZED, error.status());
        verifyNoInteractions(transactions, registrations);
    }
}

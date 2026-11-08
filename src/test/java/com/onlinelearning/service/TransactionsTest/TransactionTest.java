package com.onlinelearning.service.TransactionsTest;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.onlinelearning.dto.management.TransactionResponse;
import com.onlinelearning.entity.*;
import com.onlinelearning.exception.ApiException;
import com.onlinelearning.repository.CourseRegistrationRepository;
import com.onlinelearning.repository.TransactionRepository;
import com.onlinelearning.service.impl.ManagementReadServiceImpl;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
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
class STransactionTest {
    @Mock CourseRegistrationRepository registrations;
    @Mock TransactionRepository transactions;
    @InjectMocks ManagementReadServiceImpl service;
    Transaction transaction;

    @BeforeEach
    void setUp() {
        authenticate("MANAGER");
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
        registration.setPackageNameSnapshot("Original package");
        registration.setAmount(new BigDecimal("100000"));
        Payment payment = new Payment();
        payment.setId(4L);
        payment.setRegistration(registration);
        payment.setAmount(new BigDecimal("100000"));
        payment.setProvider("SEPAY_TEST");
        payment.setProviderReference("SEPAY_TEST:123");
        payment.setStatus(PaymentStatus.SUCCEEDED);
        transaction = new Transaction();
        transaction.setId(5L);
        transaction.setPayment(payment);
        transaction.setAmount(new BigDecimal("100000"));
        transaction.setType("SEPAY_SUCCEEDED");
        transaction.setCreatedAt(Instant.parse("2026-10-01T12:00:00Z"));
        transaction.setPayload("sensitive-provider-payload");
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

    @ParameterizedTest
    @ValueSource(strings = {"MANAGER", "ADMIN"})
    void detailReturnsTransactionAndRegistrationWithoutRawPayload(String role) throws Exception {
        authenticate(role);
        when(transactions.findById(5L)).thenReturn(Optional.of(transaction));

        TransactionResponse result = service.transaction(5L);

        assertEquals(5L, result.id());
        assertEquals(4L, result.paymentId());
        assertEquals(new BigDecimal("100000"), result.amount());
        assertEquals("SEPAY_SUCCEEDED", result.type());
        assertEquals("SEPAY_TEST", result.provider());
        assertEquals("SEPAY_TEST:123", result.providerReference());
        assertEquals(PaymentStatus.SUCCEEDED, result.status());
        assertEquals(transaction.getCreatedAt(), result.createdAt());
        assertEquals(3L, result.registration().id());
        assertEquals("Test Student", result.registration().studentName());
        assertEquals("student@example.test", result.registration().studentEmail());
        assertEquals("Java Course", result.registration().courseTitle());
        assertEquals("Original package", result.registration().packageName());
        ObjectMapper json = new ObjectMapper().findAndRegisterModules();
        String serialized = json.writeValueAsString(result);
        assertFalse(json.readTree(serialized).has("payload"));
        assertFalse(serialized.contains("sensitive-provider-payload"));
        verify(transactions).findById(5L);
        verifyNoInteractions(registrations);
    }

    @ParameterizedTest
    @CsvSource({"SEPAY_PENDING,PENDING", "SEPAY_EXPIRED,CANCELLED", "VNPAY_FAILED,FAILED"})
    void historicalTransactionStatusOverridesCurrentPaymentStatus(String type, PaymentStatus expected) {
        transaction.setType(type);
        when(transactions.findById(5L)).thenReturn(Optional.of(transaction));

        TransactionResponse result = service.transaction(5L);

        assertEquals(expected, result.status());
        assertEquals(PaymentStatus.SUCCEEDED, transaction.getPayment().getStatus());
    }

    @Test
    void legacyVnpayTransactionKeepsItsProviderAndTransactionReference() {
        transaction.setType("VNPAY_SUCCEEDED");
        when(transactions.findById(5L)).thenReturn(Optional.of(transaction));

        TransactionResponse result = service.transaction(5L);

        assertEquals("VNPAY", result.provider());
        assertEquals("OLS5", result.providerReference());
    }

    @Test
    void unknownLegacyTypeFallsBackToPaymentStatus() {
        transaction.setType("PAYMENT");
        transaction.getPayment().setStatus(PaymentStatus.FAILED);
        when(transactions.findById(5L)).thenReturn(Optional.of(transaction));

        assertEquals(PaymentStatus.FAILED, service.transaction(5L).status());
    }

    @Test
    void missingTransactionReturnsNotFound() {
        ApiException error = assertThrows(ApiException.class, () -> service.transaction(99L));

        assertEquals(HttpStatus.NOT_FOUND, error.status());
        verify(transactions).findById(99L);
        verifyNoInteractions(registrations);
    }

    @ParameterizedTest
    @ValueSource(strings = {"STUDENT", "EXPERT"})
    void nonManagementRoleCannotReadDetail(String role) {
        authenticate(role);

        ApiException error = assertThrows(ApiException.class, () -> service.transaction(5L));

        assertEquals(HttpStatus.FORBIDDEN, error.status());
        verifyNoInteractions(transactions, registrations);
    }

    @Test
    void missingAuthenticationIsRejected() {
        SecurityContextHolder.clearContext();

        ApiException error = assertThrows(ApiException.class, () -> service.transaction(5L));

        assertEquals(HttpStatus.UNAUTHORIZED, error.status());
        verifyNoInteractions(transactions, registrations);
    }
}

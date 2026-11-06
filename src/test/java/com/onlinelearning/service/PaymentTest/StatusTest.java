package com.onlinelearning.service.PaymentTest;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.onlinelearning.api.SepayGateway;
import com.onlinelearning.config.SepayConfig;
import com.onlinelearning.dto.payment.PaymentResponse;
import com.onlinelearning.dto.payment.SepayWebhook;
import com.onlinelearning.entity.*;
import com.onlinelearning.exception.ApiException;
import com.onlinelearning.repository.*;
import com.onlinelearning.service.impl.PaymentServiceImpl;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StatusTest {
    static final String EMAIL = "student@example.test";
    static final String BANK = "MBBank";
    static final String ACCOUNT = "0123456789";
    static final String HOLDER = "TEST STUDENT";
    static final String QR_URL = "https://example.test/payment-qr";
    static final long STUDENT_ID = 1L;
    static final long COURSE_ID = 2L;
    static final long REGISTRATION_ID = 3L;
    static final long PAYMENT_ID = 4L;
    static final long TRANSACTION_ID = 5L;
    static final BigDecimal AMOUNT = new BigDecimal("100000");

    @Mock CourseRegistrationRepository registrations;
    @Mock CourseRepository courses;
    @Mock PaymentRepository payments;
    @Mock TransactionRepository transactions;
    @Mock UserRepository users;
    @Mock SepayGateway gateway;
    @Mock SepayConfig config;
    @Mock EntityManager em;

    final ObjectMapper json = new ObjectMapper();
    PaymentServiceImpl service;
    User student;
    Course course;
    CourseRegistration registration;
    Payment payment;
    Transaction transaction;
    Instant now;

    @BeforeEach
    void setUpPaymentData() throws JsonProcessingException {
        service = new PaymentServiceImpl(registrations, courses, payments, transactions,
                users, gateway, config, json, em);
        now = Instant.now().truncatedTo(ChronoUnit.SECONDS);

        Role role = new Role();
        role.setCode("STUDENT");
        student = new User();
        student.setId(STUDENT_ID);
        student.setEmail(EMAIL);
        student.setEnabled(true);
        student.getRoles().add(role);

        course = new Course();
        course.setId(COURSE_ID);
        course.setStatus(CourseStatus.PUBLISHED);

        registration = new CourseRegistration();
        registration.setId(REGISTRATION_ID);
        registration.setStudent(student);
        registration.setCourse(course);
        registration.setAmount(AMOUNT);
        registration.setPackageDurationSnapshot(PackageDuration.ONE_MONTH);
        registration.setValidityMonthsSnapshot(1);
        registration.setLifetimeSnapshot(false);

        payment = new Payment();
        payment.setId(PAYMENT_ID);
        payment.setRegistration(registration);
        payment.setAmount(AMOUNT);
        payment.setProvider("SEPAY_TEST");

        transaction = new Transaction();
        transaction.setId(TRANSACTION_ID);
        transaction.setPayment(payment);
        transaction.setAmount(AMOUNT);
        transaction.setType("SEPAY_PENDING");
        transaction.setCreatedAt(now.minusSeconds(30));
        transaction.setPayload(json.writeValueAsString(new PaymentServiceImpl.Receiver(
                BANK, ACCOUNT, ACCOUNT, HOLDER, "", true)));
    }

    void stubOwnedRegistration() {
        when(users.findByEmailIgnoreCase(EMAIL)).thenReturn(Optional.of(student));
        when(registrations.findByIdAndStudentId(REGISTRATION_ID, STUDENT_ID))
                .thenReturn(Optional.of(registration));
    }

    void stubLockedRegistration() {
        when(courses.findForUpdate(COURSE_ID)).thenReturn(Optional.of(course));
        when(registrations.findForUpdate(REGISTRATION_ID)).thenReturn(Optional.of(registration));
    }

    void stubPaymentLookup() {
        when(payments.findByRegistrationId(REGISTRATION_ID)).thenReturn(Optional.of(payment));
        when(transactions.findFirstByPaymentIdOrderByIdDesc(PAYMENT_ID))
                .thenReturn(Optional.of(transaction));
    }

    void stubQrResponse() {
        when(gateway.configured()).thenReturn(true);
        when(gateway.qrUrl(BANK, ACCOUNT, AMOUNT, "OLST5")).thenReturn(QR_URL);
    }

    private void stubStatusLookup() {
        stubOwnedRegistration();
        stubLockedRegistration();
        stubPaymentLookup();
    }

    @Test
    void pendingPaymentReturnsQrAndOriginalDeadline() {
        stubStatusLookup();
        stubQrResponse();

        PaymentResponse result = service.status(EMAIL, REGISTRATION_ID);

        assertEquals(REGISTRATION_ID, result.registrationId());
        assertEquals(PaymentStatus.PENDING, result.status());
        assertTrue(result.configured());
        assertTrue(result.testMode());
        assertEquals(QR_URL, result.qrUrl());
        assertEquals(BANK, result.bank());
        assertEquals(ACCOUNT, result.accountNumber());
        assertEquals(HOLDER, result.accountHolder());
        assertEquals("OLST5", result.transferContent());
        assertEquals(AMOUNT, result.amount());
        assertEquals(transaction.getCreatedAt().plusSeconds(300), result.expiresAt());
        assertFalse(result.expired());
        verify(payments, never()).saveAndFlush(any(Payment.class));
    }

    @Test
    void successfulPaymentReturnsStatusWithoutQr() {
        stubStatusLookup();
        payment.setStatus(PaymentStatus.SUCCEEDED);
        transaction.setType("SEPAY_SUCCEEDED");
        when(gateway.configured()).thenReturn(true);
        when(config.testMode()).thenReturn(true);

        PaymentResponse result = service.status(EMAIL, REGISTRATION_ID);

        assertEquals(PaymentStatus.SUCCEEDED, result.status());
        assertNull(result.qrUrl());
        assertNull(result.transferContent());
        assertNull(result.expiresAt());
        assertFalse(result.expired());
        verify(gateway, never()).qrUrl(any(), any(), any(), any());
    }

    @Test
    void registrationOwnedByAnotherStudentIsRejected() {
        when(users.findByEmailIgnoreCase(EMAIL)).thenReturn(Optional.of(student));

        ApiException error = assertThrows(ApiException.class,
                () -> service.status(EMAIL, REGISTRATION_ID));

        assertEquals(HttpStatus.NOT_FOUND, error.status());
        verify(registrations).findByIdAndStudentId(REGISTRATION_ID, STUDENT_ID);
        verifyNoInteractions(courses, payments, transactions, gateway);
    }

    @Test
    void expiredPaymentIsCancelledBeforeResponseIsBuilt() {
        stubStatusLookup();
        transaction.setCreatedAt(now.minusSeconds(600));
        when(gateway.configured()).thenReturn(true);
        when(config.testMode()).thenReturn(true);

        PaymentResponse result = service.status(EMAIL, REGISTRATION_ID);

        assertEquals(PaymentStatus.CANCELLED, result.status());
        assertTrue(result.expired());
        assertNull(result.qrUrl());
        assertEquals(transaction.getCreatedAt().plusSeconds(300), result.expiresAt());
        assertEquals(PaymentStatus.CANCELLED, payment.getStatus());
        assertEquals(RegistrationStatus.CANCELLED, registration.getStatus());
        assertEquals("SEPAY_EXPIRED", transaction.getType());
        assertNull(registration.getValidFrom());
    }

    @Test
    void registrationWithoutPaymentReturnsPendingResponse() {
        stubOwnedRegistration();
        stubLockedRegistration();
        when(gateway.configured()).thenReturn(true);
        when(config.testMode()).thenReturn(true);

        PaymentResponse result = service.status(EMAIL, REGISTRATION_ID);

        assertEquals(REGISTRATION_ID, result.registrationId());
        assertEquals(PaymentStatus.PENDING, result.status());
        assertEquals(AMOUNT, result.amount());
        assertTrue(result.configured());
        assertTrue(result.testMode());
        assertNull(result.qrUrl());
        assertNull(result.expiresAt());
        assertFalse(result.expired());
        verifyNoInteractions(transactions);
        verify(payments, never()).saveAndFlush(any(Payment.class));
    }

    @Test
    void blockedStudentCannotReadPaymentStatus() {
        student.setBlocked(true);
        when(users.findByEmailIgnoreCase(EMAIL)).thenReturn(Optional.of(student));

        ApiException error = assertThrows(ApiException.class,
                () -> service.status(EMAIL, REGISTRATION_ID));

        assertEquals(HttpStatus.FORBIDDEN, error.status());
        verifyNoInteractions(registrations, courses, payments, transactions, gateway);
    }
}

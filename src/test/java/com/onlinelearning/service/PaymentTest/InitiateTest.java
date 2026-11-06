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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InitiateTest {
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

    void stubTestEnvironment() {
        when(config.testMode()).thenReturn(true);
        when(config.provider()).thenReturn("SEPAY_TEST");
    }

    void stubQrResponse() {
        when(gateway.configured()).thenReturn(true);
        when(gateway.qrUrl(BANK, ACCOUNT, AMOUNT, "OLST5")).thenReturn(QR_URL);
    }

    private void stubEligibleRegistration() {
        stubOwnedRegistration();
        stubLockedRegistration();
    }

    @Test
    void createsPendingPaymentAndQrWithoutGrantingLearningAccess() throws Exception {
        stubEligibleRegistration();
        stubTestEnvironment();
        stubQrResponse();
        when(config.getBank()).thenReturn(BANK);
        when(config.getAccountNumber()).thenReturn(ACCOUNT);
        when(config.qrAccountNumber()).thenReturn(ACCOUNT);
        when(config.getAccountHolder()).thenReturn(HOLDER);
        when(config.getDescriptionPrefix()).thenReturn("");
        when(payments.saveAndFlush(any(Payment.class))).thenAnswer(invocation -> {
            Payment saved = invocation.getArgument(0);
            saved.setId(PAYMENT_ID);
            return saved;
        });
        when(transactions.saveAndFlush(any(Transaction.class))).thenAnswer(invocation -> {
            Transaction saved = invocation.getArgument(0);
            saved.setId(TRANSACTION_ID);
            saved.setCreatedAt(now);
            return saved;
        });

        PaymentResponse result = service.initiate(EMAIL, REGISTRATION_ID);

        ArgumentCaptor<Payment> paymentCaptor = ArgumentCaptor.forClass(Payment.class);
        verify(payments).saveAndFlush(paymentCaptor.capture());
        Payment savedPayment = paymentCaptor.getValue();
        assertSame(registration, savedPayment.getRegistration());
        assertEquals(AMOUNT, savedPayment.getAmount());
        assertEquals("SEPAY_TEST", savedPayment.getProvider());
        assertEquals(PaymentStatus.PENDING, savedPayment.getStatus());

        ArgumentCaptor<Transaction> transactionCaptor = ArgumentCaptor.forClass(Transaction.class);
        verify(transactions).saveAndFlush(transactionCaptor.capture());
        Transaction savedTransaction = transactionCaptor.getValue();
        assertSame(savedPayment, savedTransaction.getPayment());
        assertEquals(AMOUNT, savedTransaction.getAmount());
        assertEquals("SEPAY_PENDING", savedTransaction.getType());
        assertEquals(ACCOUNT, json.readTree(savedTransaction.getPayload()).get("account").asText());
        assertEquals(REGISTRATION_ID, result.registrationId());
        assertEquals(PaymentStatus.PENDING, result.status());
        assertEquals(QR_URL, result.qrUrl());
        assertEquals("OLST5", result.transferContent());
        assertEquals(now.plusSeconds(300), result.expiresAt());
        assertFalse(result.expired());
        assertEquals(RegistrationStatus.PENDING_PAYMENT, registration.getStatus());
        assertNull(registration.getValidFrom());
        verify(gateway).requireConfiguration();
    }

    @Test
    void repeatedRequestReusesPendingPaymentAndOriginalDeadline() {
        stubEligibleRegistration();
        stubTestEnvironment();
        stubQrResponse();
        when(payments.findByRegistrationId(REGISTRATION_ID)).thenReturn(Optional.of(payment));
        when(transactions.findFirstByPaymentIdAndTypeOrderByIdDesc(PAYMENT_ID, "SEPAY_PENDING"))
                .thenReturn(Optional.of(transaction));

        PaymentResponse first = service.initiate(EMAIL, REGISTRATION_ID);
        PaymentResponse second = service.initiate(EMAIL, REGISTRATION_ID);

        assertEquals(first.qrUrl(), second.qrUrl());
        assertEquals(first.transferContent(), second.transferContent());
        assertEquals(transaction.getCreatedAt().plusSeconds(300), second.expiresAt());
        assertEquals(first.expiresAt(), second.expiresAt());
        assertEquals(PaymentStatus.PENDING, second.status());
        assertEquals(RegistrationStatus.PENDING_PAYMENT, registration.getStatus());
        verify(payments, never()).saveAndFlush(any(Payment.class));
        verify(transactions, never()).saveAndFlush(any(Transaction.class));
    }

    @Test
    void registrationOwnedByAnotherStudentIsRejected() {
        when(users.findByEmailIgnoreCase(EMAIL)).thenReturn(Optional.of(student));

        ApiException error = assertThrows(ApiException.class,
                () -> service.initiate(EMAIL, REGISTRATION_ID));

        assertEquals(HttpStatus.NOT_FOUND, error.status());
        verify(registrations).findByIdAndStudentId(REGISTRATION_ID, STUDENT_ID);
        verifyNoInteractions(payments, transactions, gateway, courses);
    }

    @ParameterizedTest
    @EnumSource(value = CourseStatus.class, names = "PUBLISHED", mode = EnumSource.Mode.EXCLUDE)
    void unpublishedCourseCannotBePaid(CourseStatus status) {
        stubEligibleRegistration();
        course.setStatus(status);

        ApiException error = assertThrows(ApiException.class,
                () -> service.initiate(EMAIL, REGISTRATION_ID));

        assertEquals(HttpStatus.CONFLICT, error.status());
        verifyNoInteractions(payments, transactions, gateway);
    }

    @ParameterizedTest
    @EnumSource(value = RegistrationStatus.class, names = "PENDING_PAYMENT", mode = EnumSource.Mode.EXCLUDE)
    void registrationNotAwaitingPaymentIsRejected(RegistrationStatus status) {
        stubEligibleRegistration();
        registration.setStatus(status);

        ApiException error = assertThrows(ApiException.class,
                () -> service.initiate(EMAIL, REGISTRATION_ID));

        assertEquals(HttpStatus.CONFLICT, error.status());
        verifyNoInteractions(payments, transactions, gateway);
    }

    @Test
    void nonStudentCannotInitiatePayment() {
        student.getRoles().clear();
        when(users.findByEmailIgnoreCase(EMAIL)).thenReturn(Optional.of(student));

        ApiException error = assertThrows(ApiException.class,
                () -> service.initiate(EMAIL, REGISTRATION_ID));

        assertEquals(HttpStatus.FORBIDDEN, error.status());
        verifyNoInteractions(registrations, courses, payments, transactions, gateway);
    }
}

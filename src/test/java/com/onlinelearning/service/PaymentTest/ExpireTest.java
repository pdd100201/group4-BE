package com.onlinelearning.service.PaymentTest;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.onlinelearning.api.SepayGateway;
import com.onlinelearning.config.SepayConfig;
import com.onlinelearning.dto.payment.SepayWebhook;
import com.onlinelearning.entity.*;
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

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ExpireTest {
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

    void stubLockedRegistration() {
        when(courses.findForUpdate(COURSE_ID)).thenReturn(Optional.of(course));
        when(registrations.findForUpdate(REGISTRATION_ID)).thenReturn(Optional.of(registration));
    }

    void stubPaymentLookup() {
        when(payments.findByRegistrationId(REGISTRATION_ID)).thenReturn(Optional.of(payment));
        when(transactions.findFirstByPaymentIdOrderByIdDesc(PAYMENT_ID))
                .thenReturn(Optional.of(transaction));
    }

    private void stubExpiryLookup() {
        when(registrations.findById(REGISTRATION_ID)).thenReturn(Optional.of(registration));
        stubLockedRegistration();
        stubPaymentLookup();
    }

    @Test
    void overduePendingPaymentCancelsRegistrationAndExpiresTransaction() {
        stubExpiryLookup();
        transaction.setCreatedAt(now.minusSeconds(600));

        service.expire(REGISTRATION_ID);

        assertEquals(PaymentStatus.CANCELLED, payment.getStatus());
        assertEquals(RegistrationStatus.CANCELLED, registration.getStatus());
        assertEquals("SEPAY_EXPIRED", transaction.getType());
        assertNull(registration.getValidFrom());
    }

    @Test
    void unexpiredPendingPaymentRemainsUnchanged() {
        stubExpiryLookup();

        service.expire(REGISTRATION_ID);

        assertEquals(PaymentStatus.PENDING, payment.getStatus());
        assertEquals(RegistrationStatus.PENDING_PAYMENT, registration.getStatus());
        assertEquals("SEPAY_PENDING", transaction.getType());
    }

    @Test
    void successfulPaymentAndLearningAccessRemainUnchanged() {
        stubExpiryLookup();
        transaction.setCreatedAt(now.minusSeconds(600));
        transaction.setType("SEPAY_SUCCEEDED");
        payment.setStatus(PaymentStatus.SUCCEEDED);
        registration.setStatus(RegistrationStatus.ACTIVE);
        registration.setValidFrom(now.minusSeconds(600));
        registration.setValidUntil(now.plusSeconds(3600));

        service.expire(REGISTRATION_ID);

        assertEquals(PaymentStatus.SUCCEEDED, payment.getStatus());
        assertEquals(RegistrationStatus.ACTIVE, registration.getStatus());
        assertEquals("SEPAY_SUCCEEDED", transaction.getType());
        assertEquals(now.minusSeconds(600), registration.getValidFrom());
        assertEquals(now.plusSeconds(3600), registration.getValidUntil());
    }

    @Test
    void missingRegistrationIsIgnored() {
        service.expire(REGISTRATION_ID);

        verify(registrations).findById(REGISTRATION_ID);
        verifyNoInteractions(courses, payments, transactions, em);
    }

    @Test
    void missingPaymentIsIgnored() {
        when(registrations.findById(REGISTRATION_ID)).thenReturn(Optional.of(registration));
        stubLockedRegistration();

        service.expire(REGISTRATION_ID);

        assertEquals(RegistrationStatus.PENDING_PAYMENT, registration.getStatus());
        verifyNoInteractions(transactions);
    }

    @Test
    void missingTransactionLeavesPendingPaymentUnchanged() {
        when(registrations.findById(REGISTRATION_ID)).thenReturn(Optional.of(registration));
        stubLockedRegistration();
        when(payments.findByRegistrationId(REGISTRATION_ID)).thenReturn(Optional.of(payment));

        service.expire(REGISTRATION_ID);

        assertEquals(PaymentStatus.PENDING, payment.getStatus());
        assertEquals(RegistrationStatus.PENDING_PAYMENT, registration.getStatus());
    }

    @Test
    void legacyProviderIsNotExpiredBySepayRules() {
        stubExpiryLookup();
        payment.setProvider("VNPAY");
        transaction.setType("VNPAY_PENDING");
        transaction.setCreatedAt(now.minusSeconds(600));

        service.expire(REGISTRATION_ID);

        assertEquals(PaymentStatus.PENDING, payment.getStatus());
        assertEquals("VNPAY_PENDING", transaction.getType());
        assertEquals(RegistrationStatus.PENDING_PAYMENT, registration.getStatus());
    }
}

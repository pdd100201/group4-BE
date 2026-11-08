package com.onlinelearning.service.PaymentTest;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.onlinelearning.api.SepayGateway;
import com.onlinelearning.config.SepayConfig;
import com.onlinelearning.dto.payment.SepayWebhook;
import com.onlinelearning.entity.*;
import com.onlinelearning.exception.ApiException;
import com.onlinelearning.exception.PaymentExpiredException;
import com.onlinelearning.repository.*;
import com.onlinelearning.service.NotificationEvents;
import com.onlinelearning.service.impl.PaymentServiceImpl;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
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
class ConfirmTest {
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

    @Mock NotificationEvents notificationEvents;
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
        service = new PaymentServiceImpl(notificationEvents, registrations, courses, payments, transactions,
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

    void stubTestEnvironment() {
        when(config.testMode()).thenReturn(true);
        when(config.provider()).thenReturn("SEPAY_TEST");
    }

    SepayWebhook webhook(String transferType, BigDecimal amount, String account, String code) {
        String paidAt = now.atZone(ZoneId.of("Asia/Ho_Chi_Minh"))
                .format(DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm:ss"));
        return new SepayWebhook(1001L, BANK, paidAt, account, null, code,
                "Payment " + code, transferType, amount);
    }

    SepayWebhook validWebhook() {
        return webhook("in", AMOUNT, ACCOUNT, "OLST5");
    }

    private void stubConfirmLookup() {
        stubTestEnvironment();
        when(transactions.findById(TRANSACTION_ID)).thenReturn(Optional.of(transaction));
        stubLockedRegistration();
        when(payments.findByRegistrationId(REGISTRATION_ID)).thenReturn(Optional.of(payment));
    }

    private void assertNoLearningAccess() {
        assertNotEquals(RegistrationStatus.ACTIVE, registration.getStatus());
        assertNull(registration.getValidFrom());
        assertNull(registration.getValidUntil());
        assertNotEquals(PaymentStatus.SUCCEEDED, payment.getStatus());
        verify(payments, never()).saveAndFlush(any(Payment.class));
    }

    @Test
    void validPaymentActivatesRegistrationAndStoresSuccessfulTransaction() throws Exception {
        stubConfirmLookup();

        service.confirm(validWebhook());

        assertEquals(PaymentStatus.SUCCEEDED, payment.getStatus());
        assertEquals("SEPAY_TEST:1001", payment.getProviderReference());
        assertEquals("SEPAY_SUCCEEDED", transaction.getType());
        assertEquals(now, transaction.getCreatedAt());
        assertEquals(RegistrationStatus.ACTIVE, registration.getStatus());
        assertEquals(now, registration.getValidFrom());
        assertEquals(now.atZone(ZoneOffset.UTC).plusMonths(1).toInstant(), registration.getValidUntil());
        assertEquals(1001L, json.readTree(transaction.getPayload()).get("eventId").asLong());
        verify(payments).saveAndFlush(payment);
    }

    @Test
    void lifetimePaymentActivatesWithoutExpiryDate() {
        stubConfirmLookup();
        registration.setPackageDurationSnapshot(PackageDuration.LIFETIME);
        registration.setLifetimeSnapshot(true);
        registration.setValidityMonthsSnapshot(null);

        service.confirm(validWebhook());

        assertEquals(RegistrationStatus.ACTIVE, registration.getStatus());
        assertEquals(now, registration.getValidFrom());
        assertNull(registration.getValidUntil());
        verify(payments).saveAndFlush(payment);
    }

    @Test
    void duplicateWebhookDoesNotProcessPaymentAgain() {
        when(config.provider()).thenReturn("SEPAY_TEST");
        when(payments.findByProviderReference("SEPAY_TEST:1001")).thenReturn(Optional.of(payment));

        service.confirm(validWebhook());

        assertNoLearningAccess();
        verifyNoInteractions(transactions, courses, registrations);
    }

    @Test
    void duplicateDetectedAfterLockDoesNotGrantAccess() {
        stubConfirmLookup();
        when(payments.findByProviderReference("SEPAY_TEST:1001"))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(payment));

        service.confirm(validWebhook());

        assertNoLearningAccess();
        verify(em).refresh(payment);
    }

    @Test
    void mismatchedAmountIsRejected() {
        stubConfirmLookup();
        SepayWebhook event = webhook("in", AMOUNT.add(BigDecimal.ONE), ACCOUNT, "OLST5");

        ApiException error = assertThrows(ApiException.class, () -> service.confirm(event));

        assertEquals(HttpStatus.CONFLICT, error.status());
        assertNoLearningAccess();
    }

    @Test
    void wrongReceiverAccountIsRejected() {
        stubConfirmLookup();
        SepayWebhook event = webhook("in", AMOUNT, "wrong-account", "OLST5");

        ApiException error = assertThrows(ApiException.class, () -> service.confirm(event));

        assertEquals(HttpStatus.BAD_REQUEST, error.status());
        assertNoLearningAccess();
    }

    @Test
    void livePaymentCodeIsRejectedInTestEnvironment() {
        stubTestEnvironment();
        SepayWebhook event = webhook("in", AMOUNT, ACCOUNT, "OLSL5");

        ApiException error = assertThrows(ApiException.class, () -> service.confirm(event));

        assertEquals(HttpStatus.CONFLICT, error.status());
        assertNoLearningAccess();
        verifyNoInteractions(transactions, courses, registrations);
    }

    @Test
    void expiredPaymentIsCancelledAndDoesNotGrantAccess() {
        stubConfirmLookup();
        transaction.setCreatedAt(now.minusSeconds(600));

        assertThrows(PaymentExpiredException.class, () -> service.confirm(validWebhook()));

        assertEquals(PaymentStatus.CANCELLED, payment.getStatus());
        assertEquals(RegistrationStatus.CANCELLED, registration.getStatus());
        assertEquals("SEPAY_EXPIRED", transaction.getType());
        assertNoLearningAccess();
    }

    @Test
    void cancelledRegistrationCannotBeReactivatedByTransfer() {
        stubConfirmLookup();
        registration.setStatus(RegistrationStatus.CANCELLED);

        ApiException error = assertThrows(ApiException.class, () -> service.confirm(validWebhook()));

        assertEquals(HttpStatus.CONFLICT, error.status());
        assertEquals(RegistrationStatus.CANCELLED, registration.getStatus());
        assertNoLearningAccess();
    }

    @Test
    void outgoingTransferIsIgnored() {
        service.confirm(webhook("out", AMOUNT, ACCOUNT, "OLST5"));

        assertNoLearningAccess();
        verifyNoInteractions(transactions, courses, registrations);
        verify(payments, never()).findByProviderReference(any());
    }
}

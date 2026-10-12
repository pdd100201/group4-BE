package com.onlinelearning.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.onlinelearning.dto.course.*;
import com.onlinelearning.dto.registration.*;
import com.onlinelearning.dto.payment.PaymentResponse;
import com.onlinelearning.entity.*;
import com.onlinelearning.repository.*;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Isolated payment contract tests: no external API, money or real database writes. */
@SpringBootTest(properties={
    "spring.datasource.url=jdbc:h2:mem:payment_merge_test;MODE=MySQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
    "SEPAY_MODE=test","SEPAY_BANK=TESTBANK","SEPAY_ACCOUNT_NUMBER=100100",
    "SEPAY_QR_ACCOUNT=100100","SEPAY_ACCOUNT_HOLDER=Test Receiver",
    "SEPAY_WEBHOOK_API_KEY=unit-test-only-key-with-at-least-32-characters","SEPAY_DESCRIPTION_PREFIX=SWP"
})
@ActiveProfiles("test") @Transactional @AutoConfigureMockMvc
class PaymentMergeIntegrationTests {
    static final String STUDENT="student@onlinelearning.local", EXPERT="expert@onlinelearning.local";
    static final String WEBHOOK="/api/v1/public/payments/sepay/webhook";
    static final String KEY="Apikey unit-test-only-key-with-at-least-32-characters";
    @Autowired ExpertCourseService expert;
    @Autowired CoursePackageService packages;
    @Autowired StudentRegistrationService registrations;
    @Autowired PaymentService payments;
    @Autowired CourseRepository courses;
    @Autowired CourseRegistrationRepository rows;
    @Autowired PaymentRepository paymentRows;
    @Autowired TransactionRepository transactions;
    @Autowired UserRepository users;
    @Autowired EntityManager em;
    @Autowired ObjectMapper json;
    @Autowired MockMvc mvc;
    Long courseId, registrationId;
    List<CoursePackageResponse> offers;

    @BeforeEach void fixture() {
        courseId=expert.create(EXPERT,new CourseRequest("Payment merge course","Description","Java",BigDecimal.ZERO)).id();
        offers=packages.update(courseId,EXPERT,new CoursePackagesRequest(Arrays.stream(PackageDuration.values()).map(d->
            new CoursePackagesRequest.PackageInput(d,new BigDecimal("100000"),new BigDecimal("10"),true)).toList()));
        courses.findById(courseId).orElseThrow().setStatus(CourseStatus.PUBLISHED);
        registrationId=registrations.register(STUDENT,new RegistrationRequest(courseId,offers.get(0).id(),"Student","0912345678")).id();
    }
    String event(PaymentResponse qr,long eventId,String amount,String bank,String type) throws Exception {
        return json.writeValueAsString(Map.of("id",eventId,"gateway",bank,
            "transactionDate",Instant.now().atZone(ZoneId.of("Asia/Ho_Chi_Minh")).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")),
            "accountNumber","100100","code",qr.transferContent().substring(qr.transferContent().lastIndexOf(' ')+1),
            "content",qr.transferContent(),"transferType",type,"transferAmount",new BigDecimal(amount)));
    }
    void confirm(PaymentResponse qr,long eventId) throws Exception {
        mvc.perform(post(WEBHOOK).header("Authorization",KEY).contentType("application/json").content(event(qr,eventId,"90000","TESTBANK","in")))
            .andExpect(status().isOk()).andExpect(jsonPath("success").value(true));
    }
    @Test void repeatedQrInitiationReusesPaymentAndKeepsPendingWithoutAccess() throws Exception {
        var first=payments.initiate(STUDENT,registrationId);var second=payments.initiate(STUDENT,registrationId);
        assertEquals(first.qrUrl(),second.qrUrl());assertEquals(0,new BigDecimal("90000").compareTo(first.amount()));
        assertEquals(PaymentStatus.PENDING,first.status());assertTrue(first.testMode());assertTrue(first.qrUrl().startsWith("https://qr.sepay.vn/"));
        assertEquals(1,paymentRows.findAll().size());assertEquals(1,transactions.findAll().size());
        assertNull(rows.findById(registrationId).orElseThrow().getValidFrom());
        mvc.perform(get("/api/v1/student/courses").with(user(STUDENT).roles("STUDENT"))).andExpect(status().isOk()).andExpect(jsonPath("content").isEmpty());
    }
    @Test void confirmedSnapshotActivatesOnceAndAppearsInStudentCoursesAndManagerTransactions() throws Exception {
        var qr=payments.initiate(STUDENT,registrationId);
        packages.update(courseId,EXPERT,new CoursePackagesRequest(Arrays.stream(PackageDuration.values()).map(d->
            new CoursePackagesRequest.PackageInput(d,new BigDecimal("200000"),BigDecimal.ZERO,true)).toList()));
        confirm(qr,1001);
        var r=rows.findById(registrationId).orElseThrow();assertEquals(RegistrationStatus.ACTIVE,r.getStatus());
        assertEquals(0,new BigDecimal("90000").compareTo(r.getAmount()));assertNotNull(r.getValidFrom());
        assertEquals(r.getValidFrom().atZone(ZoneOffset.UTC).plusMonths(1).toInstant(),r.getValidUntil());
        Instant validFrom=r.getValidFrom(),validUntil=r.getValidUntil();
        confirm(qr,1001);confirm(qr,1002);
        assertEquals(validFrom,r.getValidFrom());assertEquals(validUntil,r.getValidUntil());assertEquals(1,transactions.findAll().size());
        assertEquals(PaymentStatus.SUCCEEDED,payments.status(STUDENT,registrationId).status());
        mvc.perform(get("/api/v1/student/courses").with(user(STUDENT).roles("STUDENT")))
            .andExpect(status().isOk()).andExpect(jsonPath("content[0].courseId").value(courseId));
        mvc.perform(get("/api/v1/manager/transactions").param("q","Payment merge course").param("status","SUCCEEDED").with(user("manager@onlinelearning.local").roles("MANAGER")))
            .andExpect(status().isOk()).andExpect(jsonPath("totalElements").value(1)).andExpect(jsonPath("items[0].status").value("SUCCEEDED"));
    }
    @Test void webhookRequiresItsApiKeyAndRejectsMismatchedAmountAndReceiver() throws Exception {
        var qr=payments.initiate(STUDENT,registrationId);
        String body=event(qr,1001,"90000","TESTBANK","in");
        mvc.perform(post(WEBHOOK).contentType("application/json").content(body)).andExpect(status().isUnauthorized());
        mvc.perform(post(WEBHOOK).header("Authorization","Apikey wrong").contentType("application/json").content(body)).andExpect(status().isUnauthorized());
        mvc.perform(post(WEBHOOK).header("Authorization",KEY).contentType("application/json").content(event(qr,1001,"1","TESTBANK","in"))).andExpect(status().isConflict());
        mvc.perform(post(WEBHOOK).header("Authorization",KEY).contentType("application/json").content(event(qr,1001,"90000","OTHERBANK","in"))).andExpect(status().isBadRequest());
        assertEquals(RegistrationStatus.PENDING_PAYMENT,rows.findById(registrationId).orElseThrow().getStatus());
        assertNull(rows.findById(registrationId).orElseThrow().getValidFrom());assertEquals(PaymentStatus.PENDING,payments.status(STUDENT,registrationId).status());
    }
    @Test void cancelledRegistrationIsNotReactivatedByLateTransfer() throws Exception {
        var qr=payments.initiate(STUDENT,registrationId);
        var r=rows.findById(registrationId).orElseThrow();r.setStatus(RegistrationStatus.CANCELLED);em.flush();
        confirm(qr,1001);assertEquals(RegistrationStatus.CANCELLED,r.getStatus());assertNull(r.getValidFrom());
        mvc.perform(get("/api/v1/student/courses").with(user(STUDENT).roles("STUDENT"))).andExpect(jsonPath("content").isEmpty());
    }
    @Test void lifetimePaymentHasNoArtificialExpiry() throws Exception {
        rows.findById(registrationId).orElseThrow().setStatus(RegistrationStatus.CANCELLED);
        registrationId=registrations.register(STUDENT,new RegistrationRequest(courseId,offers.get(2).id(),"Student","0912345678")).id();
        var qr=payments.initiate(STUDENT,registrationId);confirm(qr,1001);
        var r=rows.findById(registrationId).orElseThrow();assertTrue(r.getLifetimeSnapshot());assertNotNull(r.getValidFrom());assertNull(r.getValidUntil());
        mvc.perform(get("/api/v1/student/courses").with(user(STUDENT).roles("STUDENT"))).andExpect(jsonPath("content[0].lifetime").value(true));
    }
    @Test void wrongRoleOtherOwnerAndUnavailableCourseCannotInitiate() throws Exception {
        String endpoint="/api/v1/student/registrations/"+registrationId+"/payment";
        mvc.perform(post(endpoint)).andExpect(status().isUnauthorized());
        for(String role:new String[]{"ADMIN","MANAGER","EXPERT"}) mvc.perform(post(endpoint).with(user(role.toLowerCase()+"@onlinelearning.local").roles(role))).andExpect(status().isForbidden());
        User other=new User();other.setEmail("payment-other@example.test");other.setFullName("Other");other.setPasswordHash("test");other.setEnabled(true);other.setRoles(new HashSet<>(users.findByEmailIgnoreCase(STUDENT).orElseThrow().getRoles()));users.save(other);
        mvc.perform(post(endpoint).with(user(other.getEmail()).roles("STUDENT"))).andExpect(status().isNotFound());
        courses.findById(courseId).orElseThrow().setStatus(CourseStatus.ARCHIVED);
        mvc.perform(post(endpoint).with(user(STUDENT).roles("STUDENT"))).andExpect(status().isConflict());assertTrue(paymentRows.findAll().isEmpty());
    }
    @Test void outgoingAndWrongEnvironmentCallbacksDoNotGrantAccess() throws Exception {
        var qr=payments.initiate(STUDENT,registrationId);
        mvc.perform(post(WEBHOOK).header("Authorization",KEY).contentType("application/json").content(event(qr,1001,"90000","TESTBANK","out"))).andExpect(status().isOk());
        mvc.perform(post(WEBHOOK).header("Authorization",KEY).contentType("application/json").content(event(qr,1001,"90000","TESTBANK","in").replace("OLST","OLSL"))).andExpect(status().isConflict());
        assertEquals(RegistrationStatus.PENDING_PAYMENT,rows.findById(registrationId).orElseThrow().getStatus());assertNull(rows.findById(registrationId).orElseThrow().getValidFrom());
    }
}

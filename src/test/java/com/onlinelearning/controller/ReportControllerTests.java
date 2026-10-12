package com.onlinelearning.controller;

import com.onlinelearning.entity.*;
import com.onlinelearning.dto.report.ReportKind;
import com.onlinelearning.exception.ApiException;
import com.onlinelearning.service.ReportService;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = "app.seed-demo-users.enabled=false")
@ActiveProfiles("test")
@AutoConfigureMockMvc
@Transactional
class ReportControllerTests {
    @Autowired MockMvc mvc;
    @Autowired EntityManager em;
    @Autowired ReportService service;
    Course course;
    Course empty;
    User student;

    @BeforeEach
    void fixture() {
        student = new User();
        student.setEmail("report-fixture@example.test");
        student.setFullName("+Student");
        student.setPasswordHash("not-exposed");
        em.persist(student);
        course = new Course();
        course.setTitle("=Course,\"quoted\"");
        course.setExpert(student);
        course.setStatus(CourseStatus.PUBLISHED);
        em.persist(course);
        empty = new Course();
        empty.setTitle("Empty retained course");
        empty.setExpert(student);
        em.persist(empty);
        registration("100", RegistrationStatus.ACTIVE, PaymentStatus.SUCCEEDED, "2026-10-01T23:59:59Z", true);
        registration("200", RegistrationStatus.EXPIRED, PaymentStatus.SUCCEEDED, "2026-10-02T00:00:00Z", false);
        registration("900", RegistrationStatus.PENDING_PAYMENT, PaymentStatus.PENDING, "2026-10-01T12:00:00Z", false);
        em.flush();
    }

    private void registration(String amount, RegistrationStatus state, PaymentStatus paymentState, String date, boolean duplicate) {
        CourseRegistration r = new CourseRegistration();
        r.setStudent(student); r.setCourse(course); r.setAmount(new BigDecimal(amount)); r.setStatus(state);
        r.setCreatedAt(Instant.parse(date)); r.setNote("OLS-DEMO-REPORT-TEST");
        em.persist(r);
        Payment p = new Payment();
        p.setRegistration(r); p.setAmount(r.getAmount()); p.setStatus(paymentState); em.persist(p);
        transaction(p, date);
        if (duplicate) transaction(p, "2026-10-02T12:00:00Z");
    }
    private void transaction(Payment p, String date) {
        Transaction t = new Transaction();
        t.setPayment(p); t.setAmount(p.getAmount()); t.setType("DEMO_PAYMENT_ATTEMPT");
        t.setCreatedAt(Instant.parse(date)); t.setPayload("do-not-expose"); em.persist(t);
    }

    @Test @WithMockUser(roles="ADMIN")
    void listsAuthorizedReports() throws Exception {
        mvc.perform(get("/api/v1/admin/reports"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(3));
    }

    @Test @WithMockUser(roles="ADMIN")
    void courseCountsDoNotMultiplyWithTransactionsAndKeepEmptyCourses() throws Exception {
        mvc.perform(get("/api/v1/admin/reports/COURSES"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.records.totalElements").value(2))
                .andExpect(jsonPath("$.totalAmount").value(300))
                .andExpect(jsonPath("$.records.items[0].count").value(0))
                .andExpect(jsonPath("$.records.items[1].count").value(3))
                .andExpect(jsonPath("$.records.items[1].amount").value(300));
    }

    @Test @WithMockUser(roles="ADMIN")
    void revenueCountsSuccessfulPaymentsOnce() throws Exception {
        mvc.perform(get("/api/v1/admin/reports/REVENUE"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.records.totalElements").value(1))
                .andExpect(jsonPath("$.records.items[0].count").value(2))
                .andExpect(jsonPath("$.totalAmount").value(300));
    }

    @Test @WithMockUser(roles="ADMIN")
    void datesIncludeWholeEndDayAndUseFirstTransactionOnly() throws Exception {
        mvc.perform(get("/api/v1/admin/reports/REVENUE").param("from", "2026-10-01").param("to", "2026-10-01"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalAmount").value(100));
        mvc.perform(get("/api/v1/admin/reports/REVENUE").param("from", "2026-10-02").param("to", "2026-10-02"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalAmount").value(200));
    }

    @Test @WithMockUser(roles="ADMIN")
    void registrationsFilterStatusAndPaginateAtDatabase() throws Exception {
        mvc.perform(get("/api/v1/admin/reports/REGISTRATIONS").param("status", "ACTIVE")
                .param("courseId", course.getId().toString()).param("size", "1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.records.totalElements").value(1))
                .andExpect(jsonPath("$.records.items[0].demo").value(true))
                .andExpect(jsonPath("$.totalAmount").value(100));
        mvc.perform(get("/api/v1/admin/reports/REGISTRATIONS").param("size", "1").param("page", "1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.records.totalPages").value(3))
                .andExpect(jsonPath("$.records.items.length()").value(1)).andExpect(jsonPath("$.totalAmount").value(1200));
    }

    @Test @WithMockUser(roles="ADMIN")
    void csvUsesFiltersAndEscapesFormulasQuotesAndCommas() throws Exception {
        var response = mvc.perform(get("/api/v1/admin/reports/REGISTRATIONS/export").param("status", "ACTIVE"))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
                .andReturn().getResponse();
        String csv = new String(response.getContentAsByteArray(), java.nio.charset.StandardCharsets.UTF_8);
        assertTrue(csv.startsWith("\uFEFF"));
        assertTrue(csv.contains("\"'=Course,\"\"quoted\"\"\""));
        assertTrue(csv.contains("\"'+Student\""));
        assertEquals(2, csv.split("\r\n").length);
        assertFalse(csv.contains("do-not-expose"));
    }

    @Test @WithMockUser(roles="ADMIN")
    void csvExportsBeyondTheFirstPage() {
        for (int i = 0; i < 101; i++) {
            registration("1", RegistrationStatus.PENDING_PAYMENT, PaymentStatus.PENDING,
                    "2026-10-01T12:00:00Z", false);
        }
        em.flush();
        String csv = new String(service.export(ReportKind.REGISTRATIONS, course.getId(),
                RegistrationStatus.PENDING_PAYMENT, null, null), java.nio.charset.StandardCharsets.UTF_8);
        assertEquals(103, csv.split("\r\n").length);
    }

    @Test @WithMockUser(roles="ADMIN")
    void csvNeutralizesMultilineFormulas() {
        student.setFullName("=1\n+2");
        em.flush();
        String csv = new String(service.export(ReportKind.REGISTRATIONS, course.getId(),
                RegistrationStatus.ACTIVE, null, null), java.nio.charset.StandardCharsets.UTF_8);
        assertTrue(csv.contains("\"'=1\n+2\""));
    }

    @Test @WithMockUser(roles="ADMIN")
    void validatesFiltersAndEmptyResults() throws Exception {
        for (String path : new String[]{"COURSES?size=101", "REVENUE?status=ACTIVE",
                "REGISTRATIONS?from=2026-10-02&to=2026-10-01", "COURSES?courseId=0", "UNKNOWN"}) {
            mvc.perform(get("/api/v1/admin/reports/" + path)).andExpect(status().isBadRequest());
        }
        mvc.perform(get("/api/v1/admin/reports/REVENUE").param("courseId", empty.getId().toString()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.records.totalElements").value(0))
                .andExpect(jsonPath("$.totalAmount").value(0));
    }

    @Test @WithMockUser(roles="MANAGER")
    void managerCannotReadExportOrCallServiceDirectly() throws Exception {
        mvc.perform(get("/api/v1/admin/reports")).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/admin/reports/REVENUE/export")).andExpect(status().isForbidden());
        assertThrows(ApiException.class, () -> service.read(ReportKind.COURSES, null, null, null, null, 0, 20));
    }

    @Test @WithMockUser(roles="STUDENT")
    void studentCannotRead() throws Exception {
        mvc.perform(get("/api/v1/admin/reports/REGISTRATIONS")).andExpect(status().isForbidden());
    }

    @Test
    void anonymousCannotRead() throws Exception {
        mvc.perform(get("/api/v1/admin/reports")).andExpect(status().isUnauthorized());
    }
}

package com.onlinelearning.controller;

import com.onlinelearning.entity.*;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = "app.seed-demo-users.enabled=false")
@ActiveProfiles("test")
@AutoConfigureMockMvc
@Transactional
class ManagementReadControllerTests {
    @Autowired MockMvc mvc;
    @Autowired EntityManager em;
    CourseRegistration registration;
    Transaction transaction;

    @BeforeEach
    void fixture() {
        User student = new User();
        student.setEmail("management-fixture@example.test");
        student.setFullName("Management Student");
        student.setPasswordHash("not-exposed");
        em.persist(student);
        Course course = new Course();
        course.setTitle("Read API Course");
        course.setExpert(student);
        em.persist(course);
        registration = new CourseRegistration();
        registration.setStudent(student);
        registration.setCourse(course);
        registration.setAmount(new BigDecimal("100000"));
        registration.setPackageNameSnapshot("Original package");
        registration.setPriceSnapshot(new BigDecimal("120000"));
        registration.setDiscountSnapshot(new BigDecimal("20000"));
        registration.setCreatedAt(Instant.parse("2026-10-01T23:59:59Z"));
        em.persist(registration);
        Payment payment = new Payment();
        payment.setRegistration(registration);
        payment.setAmount(registration.getAmount());
        payment.setStatus(PaymentStatus.SUCCEEDED);
        payment.setProvider("VNPAY");
        payment.setProviderReference("fixture-provider-ref");
        em.persist(payment);
        transaction = new Transaction();
        transaction.setPayment(payment);
        transaction.setAmount(payment.getAmount());
        transaction.setType("PAYMENT");
        transaction.setPayload("sensitive-provider-data");
        transaction.setCreatedAt(Instant.parse("2026-10-01T23:59:59Z"));
        em.persist(transaction);
        em.flush();
        em.clear();
    }

    @Test @WithMockUser(roles = "MANAGER")
    void registrationListFiltersAndPaginatesAtDatabase() throws Exception {
        mvc.perform(get("/api/v1/manager/registrations").param("q", "management-fixture")
                .param("status", "PENDING_PAYMENT").param("size", "1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.items[0].id").value(registration.getId()))
                .andExpect(jsonPath("$.items[0].packageName").value("Original package"))
                .andExpect(jsonPath("$.items[0].passwordHash").doesNotExist());
        mvc.perform(get("/api/v1/manager/registrations").param("q", "management-fixture")
                .param("size", "1").param("page", "1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items").isEmpty());
    }

    @Test @WithMockUser(roles = "ADMIN")
    void adminReadsSnapshotWithoutRecomputingCoursePrice() throws Exception {
        mvc.perform(get("/api/v1/admin/registrations/" + registration.getId()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.originalPrice").value(120000))
                .andExpect(jsonPath("$.discount").value(20000))
                .andExpect(jsonPath("$.amount").value(100000));
    }

    @Test @WithMockUser(roles = "MANAGER")
    void transactionFiltersIncludeLastDayAndExcludeOtherDates() throws Exception {
        mvc.perform(get("/api/v1/manager/transactions").param("provider", "vnpay")
                .param("status", "SUCCEEDED").param("q", "fixture-provider-ref")
                .param("from", "2026-10-01").param("to", "2026-10-01")
                .param("minAmount", "100000").param("maxAmount", "100000"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1));
        mvc.perform(get("/api/v1/manager/transactions").param("from", "2026-10-02"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items").isEmpty());
    }

    @Test @WithMockUser(roles = "ADMIN")
    void transactionDetailReturnsRegistrationButNotRawPayload() throws Exception {
        mvc.perform(get("/api/v1/admin/transactions/" + transaction.getId()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.registration.id").value(registration.getId()))
                .andExpect(jsonPath("$.payload").doesNotExist());
    }

    @Test @WithMockUser(roles = "MANAGER")
    void missingRecordsReturn404() throws Exception {
        mvc.perform(get("/api/v1/manager/registrations/9223372036854775807"))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/manager/transactions/9223372036854775807"))
                .andExpect(status().isNotFound());
    }

    @Test @WithMockUser(roles = "MANAGER")
    void invalidFiltersReturn400() throws Exception {
        for (String query : new String[]{"size=101", "page=-1", "from=2026-10-02&to=2026-10-01",
                "status=BOGUS", "minAmount=100&maxAmount=1", "minAmount=-1", "from=bad"}) {
            mvc.perform(get("/api/v1/manager/transactions?" + query))
                    .andExpect(status().isBadRequest());
        }
    }

    @Test @WithMockUser(roles = "STUDENT")
    void studentCannotReadAnyManagementEndpoint() throws Exception {
        for (String path : new String[]{"registrations", "registrations/" + registration.getId(),
                "transactions", "transactions/" + transaction.getId()}) {
            mvc.perform(get("/api/v1/manager/" + path)).andExpect(status().isForbidden());
            mvc.perform(get("/api/v1/admin/" + path)).andExpect(status().isForbidden());
        }
    }

    @Test
    void guestMustAuthenticate() throws Exception {
        mvc.perform(get("/api/v1/manager/registrations")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/admin/transactions")).andExpect(status().isUnauthorized());
    }

    @Test @WithMockUser(roles = "MANAGER")
    void searchWildcardsAreLiteral() throws Exception {
        mvc.perform(get("/api/v1/manager/registrations").param("q", "%"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items").isEmpty());
    }
}

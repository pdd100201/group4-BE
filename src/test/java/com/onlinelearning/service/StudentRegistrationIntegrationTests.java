package com.onlinelearning.service;

import com.onlinelearning.dto.course.*;
import com.onlinelearning.dto.registration.*;
import com.onlinelearning.entity.*;
import com.onlinelearning.exception.ApiException;
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
import java.time.Instant;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest @ActiveProfiles("test") @Transactional @AutoConfigureMockMvc
class StudentRegistrationIntegrationTests {
    @Autowired StudentRegistrationService registrations;
    @Autowired CoursePackageService packages;
    @Autowired ExpertCourseService expert;
    @Autowired CourseRegistrationRepository repository;
    @Autowired CourseRepository courses;
    @Autowired CoursePackageRepository packageRepository;
    @Autowired CourseModerationService moderation;
    @Autowired CourseCatalogService catalog;
    @Autowired UserRepository users;
    @Autowired EntityManager em;
    @Autowired MockMvc mvc;
    static final String STUDENT="student@onlinelearning.local", EXPERT="expert@onlinelearning.local";
    Long id;
    List<CoursePackageResponse> offers;

    CoursePackagesRequest prices(String price,String discount,boolean active) {
        return new CoursePackagesRequest(List.of(PackageDuration.values()).stream().map(d->new CoursePackagesRequest.PackageInput(d,new BigDecimal(price),new BigDecimal(discount),active)).toList());
    }
    @BeforeEach void setup() {
        id=expert.create(EXPERT,new CourseRequest("Registration Course","Description","Java",BigDecimal.ZERO)).id();
        offers=packages.update(id,EXPERT,prices("100001","10",true));
        courses.findById(id).orElseThrow().setStatus(CourseStatus.PUBLISHED);
    }
    RegistrationRequest request(Long p) {return new RegistrationRequest(id,p,"Student snapshot","0912345678");}
    @Test void snapshotsServerPriceAndDoesNotCreatePaymentOrGrantAccess() throws Exception {
        var r=registrations.register(STUDENT,request(offers.get(0).id()));
        assertEquals(RegistrationStatus.PENDING_PAYMENT,r.status());assertEquals(new BigDecimal("90001"),r.amount());
        assertEquals(new BigDecimal("10000"),r.discountAmount());assertNull(r.validFrom());assertNull(r.validUntil());
        assertEquals(1,r.durationMonths());assertEquals("Student snapshot",r.fullName());assertEquals(STUDENT,r.email());
        assertEquals(0L,em.createQuery("select count(p) from Payment p where p.registration.id=:id",Long.class).setParameter("id",r.id()).getSingleResult());
        assertEquals(0,repository.countCurrentAccess(users.findByEmailIgnoreCase(STUDENT).orElseThrow().getId(),id,Instant.now()));
        mvc.perform(get("/api/v1/student/registrations/"+r.id()).with(user(STUDENT).roles("STUDENT")))
            .andExpect(status().isOk()).andExpect(jsonPath("amount").value(90001)).andExpect(jsonPath("currency").value("VND"));
    }
    @Test void repeatedSubmitReopensSamePendingDespiteNewPackagePrices() {
        var first=registrations.register(STUDENT,request(offers.get(0).id()));
        packages.update(id,EXPERT,prices("200000","20",true));
        var again=registrations.register(STUDENT,request(offers.get(1).id()));
        assertEquals(first.id(),again.id());assertTrue(again.reused());assertEquals(first.packageId(),again.packageId());assertEquals(first.amount(),again.amount());
        assertEquals(1,repository.findAll((root,q,cb)->cb.equal(root.get("course").get("id"),id)).size());
    }
    @Test void activeMonthlyAndLifetimeAccessBlockRegistrationButExpiredAllowsNew() {
        var first=registrations.register(STUDENT,request(offers.get(0).id()));
        var r=repository.findById(first.id()).orElseThrow();r.setStatus(RegistrationStatus.ACTIVE);r.setValidFrom(Instant.now().minusSeconds(60));r.setValidUntil(Instant.now().plusSeconds(3600));
        assertEquals(409,assertThrows(ApiException.class,()->registrations.register(STUDENT,request(offers.get(1).id()))).status().value());
        r.setValidUntil(null);r.setLifetimeSnapshot(true);
        assertThrows(ApiException.class,()->registrations.register(STUDENT,request(offers.get(2).id())));
        r.setLifetimeSnapshot(false);r.setValidUntil(Instant.now().minusSeconds(1));
        var renewed=registrations.register(STUDENT,request(offers.get(1).id()));assertNotEquals(first.id(),renewed.id());assertEquals(12,renewed.durationMonths());
    }
    @Test void lifetimeSnapshotDoesNotUseArtificialExpiry() {
        var r=registrations.register(STUDENT,request(offers.get(2).id()));assertTrue(r.lifetime());assertNull(r.durationMonths());assertNull(r.validUntil());
    }
    @Test void hiddenCourseInactiveAndForeignPackagesCannotRegister() {
        courses.findById(id).orElseThrow().setStatus(CourseStatus.ARCHIVED);
        assertThrows(ApiException.class,()->registrations.register(STUDENT,request(offers.get(0).id())));
        courses.findById(id).orElseThrow().setStatus(CourseStatus.PUBLISHED);
        packageRepository.findById(offers.get(0).id()).orElseThrow().setActive(false);
        assertThrows(ApiException.class,()->registrations.register(STUDENT,request(offers.get(0).id())));
        Long other=expert.create(EXPERT,new CourseRequest("Other","Description","Java",BigDecimal.ZERO)).id();
        var foreign=packages.update(other,EXPERT,prices("100000","0",true)).get(0);
        assertEquals(400,assertThrows(ApiException.class,()->registrations.register(STUDENT,request(foreign.id()))).status().value());
    }
    @Test void packageOwnershipStateAndValidationAreEnforced() throws Exception {
        assertThrows(ApiException.class,()->packages.update(id,STUDENT,prices("100000","0",true)));
        User other=new User();other.setEmail("other-expert@example.test");other.setFullName("Other");other.setPasswordHash("test");other.setEnabled(true);other.setRoles(new java.util.HashSet<>(users.findByEmailIgnoreCase(EXPERT).orElseThrow().getRoles()));users.save(other);
        assertThrows(ApiException.class,()->packages.update(id,other.getEmail(),prices("100000","0",true)));
        courses.findById(id).orElseThrow().setStatus(CourseStatus.PENDING_REVIEW);
        assertEquals(409,assertThrows(ApiException.class,()->packages.update(id,EXPERT,prices("100000","0",true))).status().value());
        courses.findById(id).orElseThrow().setStatus(CourseStatus.PUBLISHED);
        assertThrows(ApiException.class,()->packages.update(id,EXPERT,prices("-1","0",true)));
        assertThrows(ApiException.class,()->packages.update(id,EXPERT,prices("100000","101",true)));
        assertThrows(ApiException.class,()->packages.update(id,EXPERT,prices("100.5","0",true)));
        mvc.perform(put("/api/v1/expert/courses/"+id+"/packages").with(user(EXPERT).roles("EXPERT")).contentType("application/json").content("{\"packages\":[]}"))
            .andExpect(status().isBadRequest());
        mvc.perform(put("/api/v1/expert/courses/"+id+"/packages").with(user(EXPERT).roles("EXPERT")).contentType("application/json").content("{\"packages\":[{\"duration\":\"BOGUS\"}]}"))
            .andExpect(status().isBadRequest());
    }
    @Test void publicPricesUseActivePackagesAndRetainedRegistrationsPreventDeletion() {
        assertEquals(new BigDecimal("90001"),catalog.getPublished(id).price());
        assertEquals(3,catalog.getPublished(id).packages().size());
        registrations.register(STUDENT,request(offers.get(0).id()));
        assertThrows(ApiException.class,()->moderation.delete(id,"manager@onlinelearning.local","Violation"));
        packages.update(id,EXPERT,prices("200000","0",false));assertTrue(catalog.getPublished(id).packages().isEmpty());assertNull(catalog.getPublished(id).price());
    }
    @Test void courseWithoutRegistrationsDeletesItsPackages() {
        moderation.delete(id,"manager@onlinelearning.local","Delete fixture");assertFalse(courses.existsById(id));assertTrue(packageRepository.findByCourseIdOrderByIdAsc(id).isEmpty());
    }
    @Test void draftExpertDeletionAlsoRemovesConfiguredPackages() {
        courses.findById(id).orElseThrow().setStatus(CourseStatus.DRAFT);
        expert.delete(id,EXPERT);assertFalse(courses.existsById(id));assertTrue(packageRepository.findByCourseIdOrderByIdAsc(id).isEmpty());
    }
    @Test void apiRejectsGuestsWrongRolesAndInvalidContactAndHidesOtherStudents() throws Exception {
        String body="{\"courseId\":"+id+",\"packageId\":"+offers.get(0).id()+",\"fullName\":\"Student\",\"phone\":\"0912345678\",\"amount\":1,\"studentId\":999}";
        mvc.perform(post("/api/v1/student/registrations").contentType("application/json").content(body)).andExpect(status().isUnauthorized());
        for(String role:new String[]{"EXPERT","MANAGER","ADMIN"}) mvc.perform(post("/api/v1/student/registrations").with(user(role.toLowerCase()+"@onlinelearning.local").roles(role)).contentType("application/json").content(body)).andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/student/registrations").with(user(STUDENT).roles("STUDENT")).contentType("application/json").content(body))
            .andExpect(status().isCreated()).andExpect(jsonPath("amount").value(90001));
        mvc.perform(post("/api/v1/student/registrations").with(user(STUDENT).roles("STUDENT")).contentType("application/json").content(body))
            .andExpect(status().isOk()).andExpect(jsonPath("reused").value(true));
        mvc.perform(post("/api/v1/student/registrations").with(user(STUDENT).roles("STUDENT")).contentType("application/json").content(body.replace("0912345678","bad"))).andExpect(status().isBadRequest());
        var r=registrations.register(STUDENT,request(offers.get(0).id()));
        User other=new User();other.setEmail("other-student@example.test");other.setFullName("Other");other.setPasswordHash("test");other.setEnabled(true);other.setRoles(new java.util.HashSet<>(users.findByEmailIgnoreCase(STUDENT).orElseThrow().getRoles()));users.save(other);
        mvc.perform(get("/api/v1/student/registrations/"+r.id()).with(user(other.getEmail()).roles("STUDENT"))).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/student/registrations").with(user(other.getEmail()).roles("STUDENT"))).andExpect(jsonPath("content").isEmpty());
        mvc.perform(get("/api/v1/student/registrations").param("size","101").with(user(STUDENT).roles("STUDENT"))).andExpect(status().isBadRequest());
    }
}

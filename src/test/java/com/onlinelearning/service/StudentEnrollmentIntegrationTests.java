package com.onlinelearning.service;

import com.onlinelearning.dto.course.CourseRequest;
import com.onlinelearning.entity.*;
import com.onlinelearning.repository.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.test.web.servlet.MockMvc;
import java.math.BigDecimal;
import java.time.Instant;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest @ActiveProfiles("test") @Transactional @AutoConfigureMockMvc
class StudentEnrollmentIntegrationTests {
    @Autowired StudentEnrollmentService enrollment;
    @Autowired ExpertCourseService courses;
    @Autowired CourseRepository courseRepository;
    @Autowired CourseRegistrationRepository registrations;
    @Autowired UserRepository users;
    @Autowired MockMvc mvc;
    static final String STUDENT="student@onlinelearning.local";
    Long course() { return courses.create("expert@onlinelearning.local",new CourseRequest("Enrollment test","Description","Java",BigDecimal.ZERO)).id(); }
    CourseRegistration registration(Long id,RegistrationStatus status,boolean current) {
        var r=new CourseRegistration();r.setCourse(courseRepository.findById(id).orElseThrow());r.setStudent(users.findByEmailIgnoreCase(STUDENT).orElseThrow());
        r.setStatus(status);r.setPackageNameSnapshot("1 năm");r.setAmount(BigDecimal.ZERO);r.setLifetimeSnapshot(false);
        r.setValidFrom(Instant.now().minusSeconds(60));r.setValidUntil(Instant.now().plusSeconds(current?3600:-1));return registrations.saveAndFlush(r);
    }
    @Test void activeAccessWinsOverPendingAndIsScopedToStudent() {
        Long id=course();var active=registration(id,RegistrationStatus.ACTIVE,true);registration(id,RegistrationStatus.PENDING_PAYMENT,true);
        var state=enrollment.read(id,STUDENT);assertTrue(state.hasAccess());assertEquals(active.getId(),state.registrationId());
        var other=new User();other.setEmail("enrollment-other@test.local");other.setFullName("Other");other.setPasswordHash("unused");other.setEnabled(true);other.setRoles(new java.util.HashSet<>(users.findByEmailIgnoreCase(STUDENT).orElseThrow().getRoles()));users.saveAndFlush(other);
        assertNull(enrollment.read(id,other.getEmail()).registrationId());
    }
    @Test void expiredAndCancelledDoNotGrantAccess() {
        Long id=course();registration(id,RegistrationStatus.ACTIVE,false);registration(id,RegistrationStatus.CANCELLED,true);
        assertFalse(enrollment.read(id,STUDENT).hasAccess());assertNull(enrollment.read(id,STUDENT).registrationId());
    }
    @Test void pendingRegistrationReturnsPaymentDestination() {
        Long id=course();var pending=registration(id,RegistrationStatus.PENDING_PAYMENT,true);
        var result=enrollment.read(id,STUDENT);assertFalse(result.hasAccess());assertEquals(pending.getId(),result.registrationId());assertEquals(RegistrationStatus.PENDING_PAYMENT,result.status());
    }
    @Test void endpointEnforcesAuthenticationAndRole() throws Exception {
        Long id=course();registration(id,RegistrationStatus.ACTIVE,true);String path="/api/v1/student/courses/"+id+"/enrollment";
        mvc.perform(get(path)).andExpect(status().isUnauthorized());
        mvc.perform(get(path).with(user("expert@onlinelearning.local").roles("EXPERT"))).andExpect(status().isForbidden());
        mvc.perform(get(path).with(user(STUDENT).roles("STUDENT"))).andExpect(status().isOk()).andExpect(jsonPath("hasAccess").value(true));
    }
}

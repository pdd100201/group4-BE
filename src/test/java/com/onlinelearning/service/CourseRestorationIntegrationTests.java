package com.onlinelearning.service;

import com.onlinelearning.dto.course.CourseRequest;
import com.onlinelearning.entity.CourseStatus;
import com.onlinelearning.repository.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest @ActiveProfiles("test") @AutoConfigureMockMvc @Transactional
class CourseRestorationIntegrationTests {
    @Autowired ExpertCourseService expert;
    @Autowired CourseRepository courses;
    @Autowired AuditLogRepository logs;
    @Autowired MockMvc mvc;
    private Long archived() {
        Long id = expert.create("expert@onlinelearning.local", new CourseRequest("Restore", "Description", "Java", BigDecimal.ZERO)).id();
        courses.findById(id).orElseThrow().setStatus(CourseStatus.ARCHIVED);
        return id;
    }
    @Test void bothModeratorsRestoreWithReasonAndAudit() throws Exception {
        for (String role : new String[]{"ADMIN", "MANAGER"}) {
            Long id = archived(); String email = role.toLowerCase() + "@onlinelearning.local";
            mvc.perform(post("/api/v1/" + role.toLowerCase() + "/courses/" + id + "/restore")
                .with(user(email).roles(role)).contentType("application/json").content("{\"reason\":\"  Issue resolved  \"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("status").value("PUBLISHED"));
            assertTrue(logs.findAll().stream().anyMatch(l -> "COURSE_RESTORED".equals(l.getAction())
                && id.toString().equals(l.getTargetId()) && email.equals(l.getActor().getEmail()) && "Issue resolved".equals(l.getMetadata())));
            mvc.perform(post("/api/v1/" + role.toLowerCase() + "/courses/" + id + "/restore")
                .with(user(email).roles(role)).contentType("application/json").content("{\"reason\":\"Again\"}"))
                .andExpect(status().isConflict());
        }
    }
    @Test void invalidReasonsAndUnauthorizedRolesCannotRestore() throws Exception {
        Long id = archived(); String path = "/api/v1/manager/courses/" + id + "/restore";
        for (String reason : new String[]{"", "   ", "x".repeat(256)})
            mvc.perform(post(path).with(user("manager@onlinelearning.local").roles("MANAGER"))
                .contentType("application/json").content("{\"reason\":\"" + reason + "\"}"))
                .andExpect(status().isBadRequest());
        for (String role : new String[]{"EXPERT", "STUDENT"})
            mvc.perform(post(path).with(user("other@test.local").roles(role)).contentType("application/json")
                .content("{\"reason\":\"Invalid actor\"}")).andExpect(status().isForbidden());
        mvc.perform(post(path).contentType("application/json").content("{\"reason\":\"Anonymous\"}"))
            .andExpect(status().isUnauthorized());
        assertEquals(CourseStatus.ARCHIVED, courses.findById(id).orElseThrow().getStatus());
        mvc.perform(get("/api/v1/admin/courses/archived").with(user("admin@onlinelearning.local").roles("ADMIN")))
            .andExpect(status().isOk()).andExpect(jsonPath("content[0].quizzes.length()").value(0));
    }
}

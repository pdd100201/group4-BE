package com.onlinelearning.service;

import com.onlinelearning.api.EmailGateway;
import com.onlinelearning.api.GoogleIdentityService;
import com.onlinelearning.dto.account.AccountStatus;
import com.onlinelearning.dto.account.CreateAccountRequest;
import com.onlinelearning.dto.account.UpdateAccountRequest;
import com.onlinelearning.dto.auth.LoginRequest;
import com.onlinelearning.dto.profile.ChangePasswordRequest;
import com.onlinelearning.dto.profile.UpdateProfileRequest;
import com.onlinelearning.entity.User;
import com.onlinelearning.exception.ApiException;
import com.onlinelearning.repository.AuthSessionRepository;
import com.onlinelearning.repository.UserRepository;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
@AutoConfigureMockMvc
class AdminPlatformIntegrationTests {
    @Autowired AdminAccountService accounts;
    @Autowired ProfileService profile;
    @Autowired DashboardService dashboard;
    @Autowired AuthService auth;
    @Autowired UserRepository users;
    @Autowired AuthSessionRepository sessions;
    @Autowired PasswordEncoder encoder;
    @Autowired MockMvc mvc;
    @MockitoBean EmailGateway emailGateway;
    @MockitoBean GoogleIdentityService googleIdentityService;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void adminCreatesSearchesAndUpdatesAnAccountWithoutReturningSecrets() {
        authenticate("admin@onlinelearning.local", "ROLE_ADMIN");
        var created = accounts.create(new CreateAccountRequest(
                "Managed Expert", "Managed.Expert@example.com", "0909000111", "EXPERT"));

        assertEquals("managed.expert@example.com", created.email());
        assertEquals(AccountStatus.ACTIVE, created.status());
        assertEquals(java.util.Set.of("ROLE_EXPERT"), created.roles());
        verify(emailGateway).sendAdminAccountInvitation(eq(created.email()), contains("/reset-password?token="));

        var page = accounts.list("managed", "EXPERT", AccountStatus.ACTIVE, 0, 10);
        assertEquals(1, page.totalElements());
        assertEquals(created.id(), page.items().getFirst().id());

        var updated = accounts.update(created.id(), new UpdateAccountRequest(
                "Managed Student", created.email(), "0909000111", "STUDENT"));
        assertEquals(java.util.Set.of("ROLE_STUDENT"), updated.roles());
        assertEquals("Managed Student", updated.fullName());
    }

    @Test
    void deactivatingAnAccountRevokesItsActiveSessions() {
        var login = auth.login(new LoginRequest("student@onlinelearning.local", "Online@123"));
        User student = users.findByEmailIgnoreCase("student@onlinelearning.local").orElseThrow();
        assertTrue(sessions.findByUserAndRevokedAtIsNull(student).stream().anyMatch(session -> session.getRevokedAt() == null));

        authenticate("admin@onlinelearning.local", "ROLE_ADMIN");
        var updated = accounts.updateStatus(student.getId(), AccountStatus.INACTIVE);

        assertEquals(AccountStatus.INACTIVE, updated.status());
        assertTrue(sessions.findByUserAndRevokedAtIsNull(student).stream()
                .allMatch(session -> session.getRevokedAt() != null));
        assertThrows(ApiException.class, () -> auth.refresh(new com.onlinelearning.dto.auth.RefreshRequest(login.refreshToken())));
    }

    @Test
    void administratorCannotRemoveTheirOwnAccess() {
        authenticate("admin@onlinelearning.local", "ROLE_ADMIN");
        User admin = users.findByEmailIgnoreCase("admin@onlinelearning.local").orElseThrow();

        assertThrows(ApiException.class, () -> accounts.updateStatus(admin.getId(), AccountStatus.BLOCKED));
        assertThrows(ApiException.class, () -> accounts.update(admin.getId(), new UpdateAccountRequest(
                admin.getFullName(), admin.getEmail(), admin.getPhone(), "STUDENT")));
    }

    @Test
    void currentUserUpdatesOnlyProfileFieldsAndPasswordChangeRevokesSessions() {
        var login = auth.login(new LoginRequest("student@onlinelearning.local", "Online@123"));
        authenticate("student@onlinelearning.local", "ROLE_STUDENT");

        var updated = profile.update(new UpdateProfileRequest(
                "Student Updated", "0909123456", "https://example.test/avatar.png"));
        assertEquals("student@onlinelearning.local", updated.email());
        assertEquals("Student Updated", updated.fullName());

        profile.changePassword(new ChangePasswordRequest("Online@123", "NewPassword123", "NewPassword123"));
        assertThrows(ApiException.class, () -> auth.refresh(new com.onlinelearning.dto.auth.RefreshRequest(login.refreshToken())));
        assertTrue(encoder.matches("NewPassword123",
                users.findByEmailIgnoreCase(updated.email()).orElseThrow().getPasswordHash()));
    }

    @Test
    void dashboardUsesRetainedDataAndNeverReturnsNegativeMetrics() {
        authenticate("admin@onlinelearning.local", "ROLE_ADMIN");
        var result = dashboard.read();

        assertNotNull(result.totalRevenue());
        assertTrue(result.totalRevenue().signum() >= 0);
        assertTrue(result.totalRegistrations() >= 0);
        assertTrue(result.activeStudents() >= 1);
        assertNotNull(result.recentRegistrations());
        assertNotNull(result.topCourses());
    }

    @Test
    void httpSecurityKeepsAdminResourcesAdminOnlyAndProfileOwnerScoped() throws Exception {
        mvc.perform(get("/api/v1/admin/accounts").with(user("student@onlinelearning.local").roles("STUDENT")))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/admin/reports").with(user("manager@onlinelearning.local").roles("MANAGER")))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/admin/dashboard").with(user("admin@onlinelearning.local").roles("ADMIN")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalRevenue").isNumber());
        mvc.perform(get("/api/v1/profile").with(user("student@onlinelearning.local").roles("STUDENT")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("student@onlinelearning.local"));
    }

    private void authenticate(String email, String role) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(email, null,
                        List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority(role))));
    }
}

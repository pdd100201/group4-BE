package com.onlinelearning.service;

import com.onlinelearning.api.EmailGateway;
import com.onlinelearning.api.GoogleIdentityService;
import com.onlinelearning.dto.auth.*;
import com.onlinelearning.exception.ApiException;
import com.onlinelearning.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class AuthServiceIntegrationTests {
    @Autowired AuthService auth;
    @Autowired UserRepository users;
    @Autowired PasswordEncoder passwordEncoder;
    @MockitoBean EmailGateway emailGateway;
    @MockitoBean GoogleIdentityService googleIdentityVerifier;

    @Test
    void demoAccountsAreEnabledAndHaveTheExpectedRoles() {
        assertDemoAccount("admin@onlinelearning.local", "ROLE_ADMIN");
        assertDemoAccount("manager@onlinelearning.local", "ROLE_MANAGER");
        assertDemoAccount("expert@onlinelearning.local", "ROLE_EXPERT");
        assertDemoAccount("student@onlinelearning.local", "ROLE_STUDENT");
    }

    @Test
    void registrationVerificationLoginRefreshAndLogoutFollowTheSessionRules() {
        String email = "student@example.com";
        auth.register(new RegisterRequest("Student One", email, "0901234567", "StrongPass123"));

        var saved = users.findByEmailIgnoreCase(email).orElseThrow();
        assertFalse(saved.isEnabled());
        assertTrue(saved.getRoles().stream().anyMatch(role -> role.authority().equals("ROLE_STUDENT")));

        var verificationUrl = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(emailGateway).sendAccountVerification(eq(email), verificationUrl.capture());
        String verificationToken = verificationUrl.getValue().substring(
                verificationUrl.getValue().indexOf("token=") + 6);
        auth.verifyEmail(new VerifyEmailRequest(verificationToken));
        assertTrue(saved.isEnabled());

        TokenResponse login = auth.login(new LoginRequest(email, "StrongPass123"));
        assertTrue(login.roles().contains("ROLE_STUDENT"));
        assertEquals("Student One", login.fullName());

        TokenResponse refreshed = auth.refresh(new RefreshRequest(login.refreshToken()));
        assertNotEquals(login.refreshToken(), refreshed.refreshToken());
        auth.logout(new RefreshRequest(refreshed.refreshToken()));

        assertThrows(ApiException.class, () -> auth.refresh(new RefreshRequest(refreshed.refreshToken())));
    }

    @Test
    void forgotPasswordCreatesAUsableResetLinkAndRevokesOldSessions() {
        String email = "student@onlinelearning.local";
        TokenResponse oldSession = auth.login(new LoginRequest(email, "Online@123"));

        auth.forgotPassword(new ForgotPasswordRequest(email));
        var resetUrl = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(emailGateway).sendPasswordReset(eq(email), resetUrl.capture());
        String resetToken = resetUrl.getValue().substring(resetUrl.getValue().indexOf("token=") + 6);

        auth.resetPassword(new ResetPasswordRequest(resetToken, "ResetPass123"));

        assertThrows(ApiException.class, () -> auth.refresh(new RefreshRequest(oldSession.refreshToken())));
        assertThrows(ApiException.class, () -> auth.login(new LoginRequest(email, "Online@123")));
        assertTrue(auth.login(new LoginRequest(email, "ResetPass123")).roles().contains("ROLE_STUDENT"));
    }

    @Test
    void registrationRejectsAnAlreadyUsedPhoneNumber() {
        auth.register(new RegisterRequest("First Student", "first.phone@example.com", "0911222333", "StrongPass123"));

        ApiException error = assertThrows(ApiException.class, () -> auth.register(
                new RegisterRequest("Second Student", "second.phone@example.com", "0911222333", "StrongPass123")));

        assertEquals("Phone number already registered", error.getMessage());
    }

    @Test
    void googleLoginCreatesAnEnabledStudentAndReusesTheLinkedIdentity() {
        String credential = "google-id-token";
        when(googleIdentityVerifier.verify(credential)).thenReturn(
                new GoogleIdentityService.GoogleIdentity("google-subject-1", "google.student@gmail.com",
                        "Google Student", "https://example.test/avatar.png", null));

        TokenResponse first = auth.googleLogin(new GoogleLoginRequest(credential));
        TokenResponse second = auth.googleLogin(new GoogleLoginRequest(credential));

        assertEquals(first.userId(), second.userId());
        assertTrue(first.roles().contains("ROLE_STUDENT"));
        var saved = users.findByGoogleSubject("google-subject-1").orElseThrow();
        assertTrue(saved.isEnabled());
        assertEquals("Google Student", saved.getFullName());
    }

    private void assertDemoAccount(String email, String expectedRole) {
        var user = users.findByEmailIgnoreCase(email).orElseThrow();
        assertTrue(user.isEnabled());
        assertFalse(user.isBlocked());
        assertTrue(passwordEncoder.matches("Online@123", user.getPasswordHash()));
        assertEquals(Set.of(expectedRole), user.getRoles().stream()
                .map(role -> role.authority()).collect(java.util.stream.Collectors.toSet()));
    }
}

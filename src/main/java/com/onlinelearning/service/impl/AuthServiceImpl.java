package com.onlinelearning.service.impl;

import com.onlinelearning.api.EmailGateway;
import com.onlinelearning.config.JwtTokenProvider;
import com.onlinelearning.api.GoogleIdentityService;
import com.onlinelearning.dto.auth.*;
import com.onlinelearning.entity.*;
import com.onlinelearning.exception.ApiException;
import com.onlinelearning.repository.*;
import com.onlinelearning.service.AuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.*;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class AuthServiceImpl implements AuthService {
    private final UserRepository users;
    private final RoleRepository roles;
    private final PasswordResetTokenRepository resetTokens;
    private final EmailVerificationTokenRepository verificationTokens;
    private final AuthSessionRepository sessions;
    private final AuditLogRepository auditLogs;
    private final PasswordEncoder encoder;
    private final JwtTokenProvider jwt;
    private final GoogleIdentityService googleIdentityVerifier;
    private final EmailGateway emailGateway;

    @Value("${app.frontend-url}") private String frontendUrl;
    @Value("${app.auth.verification-token-hours}") private long verificationTokenHours;
    @Value("${app.auth.reset-token-minutes}") private long resetTokenMinutes;
    @Value("${app.auth.max-failed-logins}") private int maxFailedLogins;
    @Value("${app.auth.lockout-minutes}") private long lockoutMinutes;

    @Override
    @Transactional(noRollbackFor = ApiException.class)
    public TokenResponse login(LoginRequest request) {
        User user = users.findByEmailIgnoreCase(normalizeEmail(request.email()))
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Invalid email or password"));
        Instant now = Instant.now();
        if (user.getLockedUntil() != null && user.getLockedUntil().isAfter(now)) {
            throw new ApiException(HttpStatus.LOCKED, "Account is temporarily locked. Please try again later");
        }
        if (user.getLockedUntil() != null) {
            user.setLockedUntil(null);
            user.setFailedLoginAttempts(0);
        }
        if (!user.isEnabled()) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Account email has not been verified");
        }
        if (user.isBlocked()) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Account is inactive or blocked");
        }
        if (!encoder.matches(request.password(), user.getPasswordHash())) {
            int failures = user.getFailedLoginAttempts() + 1;
            user.setFailedLoginAttempts(failures);
            if (failures >= maxFailedLogins) {
                user.setLockedUntil(now.plus(Duration.ofMinutes(lockoutMinutes)));
                user.setFailedLoginAttempts(0);
            }
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Invalid email or password");
        }
        user.setFailedLoginAttempts(0);
        user.setLockedUntil(null);
        record(user, "LOGIN_SUCCESS");
        return createSessionTokens(user);
    }

    @Override
    public TokenResponse googleLogin(GoogleLoginRequest request) {
        var identity = googleIdentityVerifier.verify(request.credential());
        String email = normalizeEmail(identity.email());
        User user = users.findByGoogleSubject(identity.subject()).orElseGet(() -> {
            Optional<User> existing = users.findByEmailIgnoreCase(email);
            if (existing.isPresent()) {
                User account = existing.get();
                if (account.getGoogleSubject() == null) {
                    if (!identity.isAuthoritativeForEmail()) {
                        throw new ApiException(HttpStatus.CONFLICT,
                                "Sign in with your password before linking this Google account");
                    }
                    account.setGoogleSubject(identity.subject());
                    account.setEnabled(true);
                }
                return account;
            }
            Role student = roles.findByCode("STUDENT")
                    .orElseThrow(() -> new ApiException(HttpStatus.INTERNAL_SERVER_ERROR,
                            "Roles have not been initialized"));
            User account = new User();
            account.setEmail(email);
            account.setGoogleSubject(identity.subject());
            account.setFullName(identity.fullName() == null || identity.fullName().isBlank()
                    ? email.substring(0, email.indexOf('@')) : identity.fullName().trim());
            account.setAvatarUrl(identity.pictureUrl());
            account.setPasswordHash(encoder.encode(UUID.randomUUID().toString()));
            account.setEnabled(true);
            account.getRoles().add(student);
            return users.save(account);
        });
        if (user.isBlocked()) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Account is inactive or blocked");
        }
        if (!identity.subject().equals(user.getGoogleSubject())) {
            throw new ApiException(HttpStatus.CONFLICT, "Google account does not match this user");
        }
        record(user, "GOOGLE_LOGIN_SUCCESS");
        return createSessionTokens(user);
    }

    @Override
    public void register(RegisterRequest request) {
        String email = normalizeEmail(request.email());
        String phone = blankToNull(request.phone());
        if (users.existsByEmailIgnoreCase(email)) {
            throw new ApiException(HttpStatus.CONFLICT, "Email already registered");
        }
        if (phone != null && users.existsByPhone(phone)) {
            throw new ApiException(HttpStatus.CONFLICT, "Phone number already registered");
        }
        Role student = roles.findByCode("STUDENT")
                .orElseThrow(() -> new ApiException(HttpStatus.INTERNAL_SERVER_ERROR,
                        "Roles have not been initialized"));
        User user = new User();
        user.setEmail(email);
        user.setFullName(request.fullName().trim());
        user.setPhone(phone);
        user.setPasswordHash(encoder.encode(request.password()));
        user.getRoles().add(student);
        users.save(user);
        sendVerification(user);
    }

    @Override
    public void verifyEmail(VerifyEmailRequest request) {
        EmailVerificationToken token = verificationTokens.findByTokenHash(hash(request.token()))
                .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST,
                        "Verification link is invalid or expired"));
        if (token.getUsedAt() != null || !token.getExpiresAt().isAfter(Instant.now())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Verification link is invalid or expired");
        }
        token.setUsedAt(Instant.now());
        token.getUser().setEnabled(true);
        record(token.getUser(), "EMAIL_VERIFIED");
    }

    @Override
    public void resendVerification(ForgotPasswordRequest request) {
        users.findByEmailIgnoreCase(normalizeEmail(request.email()))
                .filter(user -> !user.isEnabled() && !user.isBlocked())
                .ifPresent(this::sendVerification);
    }

    @Override
    public TokenResponse refresh(RefreshRequest request) {
        String rawToken = request.refreshToken();
        if (!jwt.valid(rawToken, JwtTokenProvider.REFRESH)) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Invalid refresh token");
        }
        AuthSession session = sessions.findBySessionId(jwt.sessionId(rawToken))
                .filter(value -> value.isActive(Instant.now()))
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Session has expired"));
        if (!MessageDigest.isEqual(session.getRefreshTokenHash().getBytes(StandardCharsets.UTF_8),
                hash(rawToken).getBytes(StandardCharsets.UTF_8))) {
            session.setRevokedAt(Instant.now());
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Invalid refresh token");
        }
        User user = session.getUser();
        if (!user.isEnabled() || user.isBlocked()) {
            session.setRevokedAt(Instant.now());
            throw new ApiException(HttpStatus.FORBIDDEN, "Account is inactive or blocked");
        }
        return rotateTokens(user, session);
    }

    @Override
    public void logout(RefreshRequest request) {
        String token = request.refreshToken();
        if (jwt.valid(token, JwtTokenProvider.REFRESH)) {
            sessions.findBySessionId(jwt.sessionId(token)).ifPresent(session -> {
                if (MessageDigest.isEqual(session.getRefreshTokenHash().getBytes(StandardCharsets.UTF_8),
                        hash(token).getBytes(StandardCharsets.UTF_8)) && session.getRevokedAt() == null) {
                    session.setRevokedAt(Instant.now());
                    record(session.getUser(), "LOGOUT");
                }
            });
        }
    }

    @Override
    public void forgotPassword(ForgotPasswordRequest request) {
        users.findByEmailIgnoreCase(normalizeEmail(request.email()))
                .filter(User::isEnabled).filter(user -> !user.isBlocked())
                .ifPresent(user -> {
                    resetTokens.deleteByUserAndUsedAtIsNull(user);
                    String rawToken = secureToken();
                    PasswordResetToken token = new PasswordResetToken();
                    token.setUser(user);
                    token.setTokenHash(hash(rawToken));
                    token.setExpiresAt(Instant.now().plus(Duration.ofMinutes(resetTokenMinutes)));
                    resetTokens.save(token);
                    emailGateway.sendPasswordReset(user.getEmail(),
                            frontendUrl + "/reset-password?token=" + rawToken);
                });
    }

    @Override
    public void resetPassword(ResetPasswordRequest request) {
        PasswordResetToken token = resetTokens.findByTokenHash(hash(request.token()))
                .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST,
                        "Reset token is invalid or expired"));
        if (token.getUsedAt() != null || !token.getExpiresAt().isAfter(Instant.now())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Reset token is invalid or expired");
        }
        User user = token.getUser();
        user.setPasswordHash(encoder.encode(request.newPassword()));
        user.setFailedLoginAttempts(0);
        user.setLockedUntil(null);
        token.setUsedAt(Instant.now());
        sessions.findByUserAndRevokedAtIsNull(user)
                .forEach(session -> session.setRevokedAt(Instant.now()));
        record(user, "PASSWORD_RESET");
    }

    private TokenResponse createSessionTokens(User user) {
        AuthSession session = new AuthSession();
        session.setSessionId(UUID.randomUUID().toString());
        session.setUser(user);
        session.setExpiresAt(jwt.refreshExpiresAt());
        session.setRefreshTokenHash("pending");
        sessions.save(session);
        return rotateTokens(user, session);
    }

    private TokenResponse rotateTokens(User user, AuthSession session) {
        var authorities = user.getRoles().stream()
                .map(role -> new SimpleGrantedAuthority(role.authority())).toList();
        String refreshToken = jwt.refreshToken(user.getEmail(), session.getSessionId());
        session.setRefreshTokenHash(hash(refreshToken));
        session.setExpiresAt(jwt.refreshExpiresAt());
        Set<String> roleNames = user.getRoles().stream().map(Role::authority)
                .collect(Collectors.toUnmodifiableSet());
        return new TokenResponse(
                jwt.accessToken(user.getEmail(), session.getSessionId(), authorities),
                refreshToken, "Bearer", user.getId(), user.getEmail(), user.getFullName(), roleNames);
    }

    private void sendVerification(User user) {
        verificationTokens.deleteByUserAndUsedAtIsNull(user);
        String rawToken = secureToken();
        EmailVerificationToken token = new EmailVerificationToken();
        token.setUser(user);
        token.setTokenHash(hash(rawToken));
        token.setExpiresAt(Instant.now().plus(Duration.ofHours(verificationTokenHours)));
        verificationTokens.save(token);
        emailGateway.sendAccountVerification(user.getEmail(),
                frontendUrl + "/verify-email?token=" + rawToken);
    }

    private void record(User user, String action) {
        AuditLog log = new AuditLog();
        log.setActor(user);
        log.setAction(action);
        log.setTargetType("USER");
        log.setTargetId(String.valueOf(user.getId()));
        log.setOccurredAt(Instant.now());
        auditLogs.save(log);
    }

    private String normalizeEmail(String email) { return email.trim().toLowerCase(Locale.ROOT); }
    private String blankToNull(String value) { return value == null || value.isBlank() ? null : value.trim(); }
    private String secureToken() { return UUID.randomUUID() + "-" + UUID.randomUUID(); }

    private String hash(String value) {
        try {
            byte[] bytes = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(bytes);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}

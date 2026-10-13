package com.onlinelearning.service.impl;

import com.onlinelearning.api.EmailGateway;
import com.onlinelearning.dto.account.AccountResponse;
import com.onlinelearning.dto.account.AccountStatus;
import com.onlinelearning.dto.account.CreateAccountRequest;
import com.onlinelearning.dto.account.UpdateAccountRequest;
import com.onlinelearning.dto.management.ManagementPage;
import com.onlinelearning.entity.AuditLog;
import com.onlinelearning.entity.PasswordResetToken;
import com.onlinelearning.entity.Role;
import com.onlinelearning.entity.User;
import com.onlinelearning.exception.ApiException;
import com.onlinelearning.repository.AuditLogRepository;
import com.onlinelearning.repository.AuthSessionRepository;
import com.onlinelearning.repository.PasswordResetTokenRepository;
import com.onlinelearning.repository.RoleRepository;
import com.onlinelearning.repository.UserRepository;
import com.onlinelearning.service.AdminAccountService;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class AdminAccountServiceImpl implements AdminAccountService {
    private final UserRepository users;
    private final RoleRepository roles;
    private final AuthSessionRepository sessions;
    private final PasswordResetTokenRepository resetTokens;
    private final AuditLogRepository auditLogs;
    private final PasswordEncoder encoder;
    private final EmailGateway emailGateway;

    @Value("${app.frontend-url}") private String frontendUrl;
    @Value("${app.auth.reset-token-minutes}") private long resetTokenMinutes;

    @Override
    @Transactional(readOnly = true)
    public ManagementPage<AccountResponse> list(String query, String roleCode, AccountStatus status,
            int page, int size) {
        validatePage(page, size);
        String role = normalizeRole(roleCode, false);
        Boolean enabled = status == null ? null : status != AccountStatus.INACTIVE;
        Boolean blocked = status == null ? null : status == AccountStatus.BLOCKED;
        var result = users.search(blankToNull(query), role, enabled, blocked,
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "id")));
        return ManagementPage.from(result.map(this::response));
    }

    @Override
    @Transactional(readOnly = true)
    public AccountResponse detail(long id) {
        return response(find(id));
    }

    @Override
    public AccountResponse create(CreateAccountRequest request) {
        String email = normalizeEmail(request.email());
        String phone = blankToNull(request.phone());
        ensureUnique(email, phone, null);
        User user = new User();
        user.setEmail(email);
        user.setFullName(request.fullName().trim());
        user.setPhone(phone);
        user.setPasswordHash(encoder.encode(UUID.randomUUID().toString() + UUID.randomUUID()));
        user.setEnabled(true);
        user.setBlocked(false);
        user.setRoles(Set.of(role(normalizeRole(request.roleCode(), true))));
        users.save(user);
        sendSetupLink(user);
        record(currentAdmin(), user, "ADMIN_ACCOUNT_CREATED", "role=" + request.roleCode());
        return response(user);
    }

    @Override
    public AccountResponse update(long id, UpdateAccountRequest request) {
        User actor = currentAdmin();
        User target = find(id);
        String email = normalizeEmail(request.email());
        String phone = blankToNull(request.phone());
        String roleCode = normalizeRole(request.roleCode(), true);
        ensureUnique(email, phone, target);
        if (actor.getId().equals(target.getId()) && target.getRoles().stream()
                .noneMatch(existing -> existing.getCode().equals(roleCode))) {
            throw new ApiException(HttpStatus.CONFLICT, "You cannot change your own administrator role");
        }
        target.setEmail(email);
        target.setFullName(request.fullName().trim());
        target.setPhone(phone);
        target.setRoles(Set.of(role(roleCode)));
        record(actor, target, "ADMIN_ACCOUNT_UPDATED", "role=" + roleCode);
        return response(target);
    }

    @Override
    public AccountResponse updateStatus(long id, AccountStatus status) {
        User actor = currentAdmin();
        User target = find(id);
        if (actor.getId().equals(target.getId()) && status != AccountStatus.ACTIVE) {
            throw new ApiException(HttpStatus.CONFLICT, "You cannot deactivate or block your own account");
        }
        switch (status) {
            case ACTIVE -> { target.setEnabled(true); target.setBlocked(false); }
            case INACTIVE -> { target.setEnabled(false); target.setBlocked(false); }
            case BLOCKED -> { target.setEnabled(true); target.setBlocked(true); }
        }
        target.setFailedLoginAttempts(0);
        target.setLockedUntil(null);
        if (status != AccountStatus.ACTIVE) {
            sessions.findByUserAndRevokedAtIsNull(target)
                    .forEach(session -> session.setRevokedAt(Instant.now()));
        }
        record(actor, target, "ADMIN_ACCOUNT_STATUS_UPDATED", "status=" + status);
        return response(target);
    }

    private void sendSetupLink(User user) {
        resetTokens.deleteByUserAndUsedAtIsNull(user);
        String raw = UUID.randomUUID() + "-" + UUID.randomUUID();
        PasswordResetToken token = new PasswordResetToken();
        token.setUser(user);
        token.setTokenHash(hash(raw));
        token.setExpiresAt(Instant.now().plus(Duration.ofMinutes(resetTokenMinutes)));
        resetTokens.save(token);
        emailGateway.sendAdminAccountInvitation(user.getEmail(),
                frontendUrl + "/reset-password?token=" + raw);
    }

    private void ensureUnique(String email, String phone, User current) {
        users.findByEmailIgnoreCase(email).filter(found -> current == null || !found.getId().equals(current.getId()))
                .ifPresent(found -> { throw new ApiException(HttpStatus.CONFLICT, "Email already registered"); });
        if (phone != null && users.existsByPhone(phone) && (current == null || !phone.equals(current.getPhone()))) {
            throw new ApiException(HttpStatus.CONFLICT, "Phone number already registered");
        }
    }

    private User currentAdmin() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Authentication required");
        }
        return users.findByEmailIgnoreCase(authentication.getName())
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Administrator not found"));
    }

    private User find(long id) {
        if (id < 1) throw new ApiException(HttpStatus.BAD_REQUEST, "Account id is invalid");
        return users.findById(id).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Account not found"));
    }

    private Role role(String code) {
        return roles.findByCode(code)
                .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "Role is invalid"));
    }

    private String normalizeRole(String value, boolean required) {
        if (value == null || value.isBlank()) {
            if (required) throw new ApiException(HttpStatus.BAD_REQUEST, "Role is required");
            return null;
        }
        String code = value.trim().toUpperCase(Locale.ROOT).replace("ROLE_", "");
        if (!Set.of("ADMIN", "MANAGER", "EXPERT", "STUDENT").contains(code)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Role is invalid");
        }
        return code;
    }

    private AccountResponse response(User user) {
        return new AccountResponse(user.getId(), user.getEmail(), user.getFullName(), user.getPhone(),
                user.getAvatarUrl(), user.getRoles().stream().map(Role::authority)
                        .collect(Collectors.toUnmodifiableSet()), status(user), user.getLockedUntil());
    }

    private AccountStatus status(User user) {
        if (user.isBlocked()) return AccountStatus.BLOCKED;
        return user.isEnabled() ? AccountStatus.ACTIVE : AccountStatus.INACTIVE;
    }

    private void record(User actor, User target, String action, String metadata) {
        AuditLog log = new AuditLog();
        log.setActor(actor);
        log.setAction(action);
        log.setTargetType("USER");
        log.setTargetId(String.valueOf(target.getId()));
        log.setMetadata(metadata);
        log.setOccurredAt(Instant.now());
        auditLogs.save(log);
    }

    private void validatePage(int page, int size) {
        if (page < 0 || size < 1 || size > 100) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Invalid pagination");
        }
    }

    private String normalizeEmail(String email) { return email.trim().toLowerCase(Locale.ROOT); }
    private String blankToNull(String value) { return value == null || value.isBlank() ? null : value.trim(); }

    private String hash(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}

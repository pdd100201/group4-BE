package com.onlinelearning.service.impl;

import com.onlinelearning.dto.profile.ChangePasswordRequest;
import com.onlinelearning.dto.profile.ProfileResponse;
import com.onlinelearning.dto.profile.UpdateProfileRequest;
import com.onlinelearning.entity.AuditLog;
import com.onlinelearning.entity.Role;
import com.onlinelearning.entity.User;
import com.onlinelearning.exception.ApiException;
import com.onlinelearning.repository.AuditLogRepository;
import com.onlinelearning.repository.AuthSessionRepository;
import com.onlinelearning.repository.UserRepository;
import com.onlinelearning.service.ProfileService;
import java.time.Instant;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class ProfileServiceImpl implements ProfileService {
    private final UserRepository users;
    private final AuthSessionRepository sessions;
    private final AuditLogRepository auditLogs;
    private final PasswordEncoder encoder;

    @Override
    @Transactional(readOnly = true)
    public ProfileResponse current() {
        return response(currentUser());
    }

    @Override
    public ProfileResponse update(UpdateProfileRequest request) {
        User user = currentUser();
        String phone = blankToNull(request.phone());
        if (phone != null && users.existsByPhone(phone) && !phone.equals(user.getPhone())) {
            throw new ApiException(HttpStatus.CONFLICT, "Phone number already registered");
        }
        user.setFullName(request.fullName().trim());
        user.setPhone(phone);
        user.setAvatarUrl(blankToNull(request.avatarUrl()));
        record(user, "PROFILE_UPDATED");
        return response(user);
    }

    @Override
    public void changePassword(ChangePasswordRequest request) {
        User user = currentUser();
        if (!encoder.matches(request.currentPassword(), user.getPasswordHash())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Current password is incorrect");
        }
        if (!request.newPassword().equals(request.confirmation())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Password confirmation does not match");
        }
        if (encoder.matches(request.newPassword(), user.getPasswordHash())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "New password must be different");
        }
        user.setPasswordHash(encoder.encode(request.newPassword()));
        sessions.findByUserAndRevokedAtIsNull(user)
                .forEach(session -> session.setRevokedAt(Instant.now()));
        record(user, "PASSWORD_CHANGED");
    }

    private User currentUser() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Authentication required");
        }
        return users.findByEmailIgnoreCase(authentication.getName())
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "User not found"));
    }

    private ProfileResponse response(User user) {
        return new ProfileResponse(user.getId(), user.getEmail(), user.getFullName(), user.getPhone(),
                user.getAvatarUrl(), user.getRoles().stream().map(Role::authority)
                        .collect(Collectors.toUnmodifiableSet()));
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
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
}

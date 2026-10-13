package com.onlinelearning.dto.account;

import java.time.Instant;
import java.util.Set;

public record AccountResponse(Long id, String email, String fullName, String phone,
        String avatarUrl, Set<String> roles, AccountStatus status, Instant lockedUntil) {}

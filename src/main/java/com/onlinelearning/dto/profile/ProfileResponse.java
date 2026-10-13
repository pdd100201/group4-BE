package com.onlinelearning.dto.profile;

import java.util.Set;

public record ProfileResponse(Long id, String email, String fullName, String phone,
        String avatarUrl, Set<String> roles) {}

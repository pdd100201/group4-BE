package com.onlinelearning.dto.profile;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UpdateProfileRequest(
        @NotBlank @Size(max = 150) String fullName,
        @Pattern(regexp = "^$|^[0-9+() .-]{7,20}$", message = "Phone number is invalid") String phone,
        @Size(max = 1000) String avatarUrl) {}

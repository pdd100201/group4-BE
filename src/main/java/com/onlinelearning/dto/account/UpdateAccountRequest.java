package com.onlinelearning.dto.account;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UpdateAccountRequest(
        @NotBlank @Size(max = 150) String fullName,
        @Email @NotBlank @Size(max = 255) String email,
        @Pattern(regexp = "^$|^[0-9+() .-]{7,20}$", message = "Phone number is invalid") String phone,
        @NotBlank @Pattern(regexp = "ADMIN|MANAGER|EXPERT|STUDENT", message = "Role is invalid") String roleCode) {}

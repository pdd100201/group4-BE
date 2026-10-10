package com.onlinelearning.dto.auth;
import jakarta.validation.constraints.*;
public record RegisterRequest(
        @NotBlank @Size(max=150) String fullName,
        @Email @NotBlank @Size(max=255) String email,
        @Pattern(regexp="^$|^[0-9+() .-]{7,20}$", message="Phone number is invalid") String phone,
        @NotBlank @Size(min=8,max=72) String password) {}

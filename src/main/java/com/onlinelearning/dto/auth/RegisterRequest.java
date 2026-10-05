package com.onlinelearning.dto.auth;
import jakarta.validation.constraints.*; public record RegisterRequest(@NotBlank String fullName, @Email @NotBlank String email, @Size(min=8,max=100) String password) {}

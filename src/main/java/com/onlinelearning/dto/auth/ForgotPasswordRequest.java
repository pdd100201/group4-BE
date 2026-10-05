package com.onlinelearning.dto.auth;
import jakarta.validation.constraints.*; public record ForgotPasswordRequest(@Email @NotBlank String email) {}

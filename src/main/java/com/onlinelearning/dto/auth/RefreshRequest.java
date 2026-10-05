package com.onlinelearning.dto.auth;
import jakarta.validation.constraints.*; public record RefreshRequest(@NotBlank String refreshToken) {}

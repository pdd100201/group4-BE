package com.onlinelearning.dto.auth;
import jakarta.validation.constraints.*; public record LoginRequest(@Email @NotBlank String email, @NotBlank @Size(max=72) String password) {}

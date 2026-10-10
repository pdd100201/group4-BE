package com.onlinelearning.dto.auth;
import java.util.*; public record TokenResponse(String accessToken, String refreshToken, String tokenType, Long userId, String email, String fullName, Set<String> roles) {}

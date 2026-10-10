package com.onlinelearning.controller;

import com.onlinelearning.common.ApiMessage;
import com.onlinelearning.dto.auth.ForgotPasswordRequest;
import com.onlinelearning.dto.auth.LoginRequest;
import com.onlinelearning.dto.auth.GoogleLoginRequest;
import com.onlinelearning.dto.auth.RefreshRequest;
import com.onlinelearning.dto.auth.RegisterRequest;
import com.onlinelearning.dto.auth.ResetPasswordRequest;
import com.onlinelearning.dto.auth.TokenResponse;
import com.onlinelearning.dto.auth.VerifyEmailRequest;
import com.onlinelearning.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {
    private final AuthService auth;

    @PostMapping("/login")
    public TokenResponse login(@Valid @RequestBody LoginRequest request) {
        return auth.login(request);
    }

    @PostMapping("/google")
    public TokenResponse googleLogin(@Valid @RequestBody GoogleLoginRequest request) {
        return auth.googleLogin(request);
    }

    @PostMapping("/register")
    public ResponseEntity<ApiMessage> register(@Valid @RequestBody RegisterRequest request) {
        auth.register(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new ApiMessage("Registration successful. Verify your email to activate the account."));
    }

    @PostMapping("/refresh")
    public TokenResponse refresh(@Valid @RequestBody RefreshRequest request) {
        return auth.refresh(request);
    }

    @PostMapping("/verify-email")
    public ApiMessage verifyEmail(@Valid @RequestBody VerifyEmailRequest request) {
        auth.verifyEmail(request);
        return new ApiMessage("Email verified. Your account is now active.");
    }

    @PostMapping("/resend-verification")
    public ApiMessage resendVerification(@Valid @RequestBody ForgotPasswordRequest request) {
        auth.resendVerification(request);
        return new ApiMessage("If the account is pending verification, a new link will be sent.");
    }

    @PostMapping("/logout")
    public ApiMessage logout(@Valid @RequestBody RefreshRequest request) {
        auth.logout(request);
        return new ApiMessage("Logged out successfully");
    }

    @PostMapping("/forgot-password")
    public ApiMessage forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        auth.forgotPassword(request);
        return new ApiMessage("If the email is registered, a reset link will be sent.");
    }

    @PostMapping("/reset-password")
    public ApiMessage resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        auth.resetPassword(request);
        return new ApiMessage("Password updated");
    }
}

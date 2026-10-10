package com.onlinelearning.service;
import com.onlinelearning.dto.auth.*;
public interface AuthService {
    TokenResponse login(LoginRequest request);
    TokenResponse googleLogin(GoogleLoginRequest request);
    void register(RegisterRequest request);
    void verifyEmail(VerifyEmailRequest request);
    void resendVerification(ForgotPasswordRequest request);
    TokenResponse refresh(RefreshRequest request);
    void logout(RefreshRequest request);
    void forgotPassword(ForgotPasswordRequest request);
    void resetPassword(ResetPasswordRequest request);
}

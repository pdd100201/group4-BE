package com.onlinelearning.service;
import com.onlinelearning.dto.auth.*;
public interface AuthService { TokenResponse login(LoginRequest request); void register(RegisterRequest request); TokenResponse refresh(RefreshRequest request); void forgotPassword(ForgotPasswordRequest request); void resetPassword(ResetPasswordRequest request); }

package com.onlinelearning.api;

/** Boundary for outgoing email; services do not depend on the SMTP library. */
public interface EmailGateway {
    void sendAccountVerification(String recipient, String verificationUrl);
    void sendPasswordReset(String recipient, String resetUrl);
}

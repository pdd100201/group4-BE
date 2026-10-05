package com.onlinelearning.api;

/** Boundary for outgoing email; services do not depend on the SMTP library. */
public interface EmailGateway {
    void sendPasswordReset(String recipient, String resetUrl);
}

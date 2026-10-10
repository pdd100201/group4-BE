package com.onlinelearning.api;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class SmtpEmailGateway implements EmailGateway {
    private final JavaMailSender mailSender;

    @Value("${app.mail.from:no-reply@localhost}")
    private String sender;

    @Override
    public void sendAccountVerification(String recipient, String verificationUrl) {
        send(recipient, "Online Learning - verify your account",
                "Open this link to verify and activate your account: " + verificationUrl);
    }

    @Override
    public void sendPasswordReset(String recipient, String resetUrl) {
        send(recipient, "Online Learning - password reset",
                "Open this link to reset your password: " + resetUrl);
    }

    private void send(String recipient, String subject, String body) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(sender);
        message.setTo(recipient);
        message.setSubject(subject);
        message.setText(body);
        mailSender.send(message);
    }
}

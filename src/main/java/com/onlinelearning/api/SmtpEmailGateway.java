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
    public void sendPasswordReset(String recipient, String resetUrl) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(sender);
        message.setTo(recipient);
        message.setSubject("Online Learning - password reset");
        message.setText("Open this link to reset your password: " + resetUrl);
        mailSender.send(message);
    }
}

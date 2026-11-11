package com.onlinelearning.api;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

@lombok.extern.slf4j.Slf4j
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

    @Override
    public void sendAdminAccountInvitation(String recipient, String setupUrl) {
        send(recipient, "Online Learning - your account is ready",
                "An administrator created an account for you. Open this one-time link to set your password: "
                        + setupUrl);
    }

    private void send(String recipient, String subject, String body) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(sender);
        message.setTo(recipient);
        message.setSubject(subject);
        message.setText(body);
        try {
            mailSender.send(message);
        } catch (MailException failure) {
            // Do not log recipients, reset URLs, message bodies or SMTP credentials.
            logMailFailure(failure);
            if (failure instanceof org.springframework.mail.MailSendException sendFailure) {
                for (Exception detail : sendFailure.getFailedMessages().values()) logMailFailure(detail);
            }
            throw failure;
        }
    }
    private void logMailFailure(Throwable failure) {
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            String reason = cause.getMessage() == null ? "No detail" : cause.getMessage();
            reason = reason.replaceAll("https?://\\S+", "[url]")
                    .replaceAll("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+", "[email]")
                    .replaceAll("[\r\n]+", " ");
            if (reason.length() > 1200) reason = reason.substring(0, 1200);
            log.warn("Email delivery failed: type={}, reason={}", cause.getClass().getSimpleName(), reason);
        }
    }
}

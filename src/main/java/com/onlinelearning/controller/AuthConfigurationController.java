package com.onlinelearning.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AuthConfigurationController {
    private final String googleClientId;

    public AuthConfigurationController(@Value("${app.google.client-id:}") String googleClientId) {
        this.googleClientId = googleClientId.trim();
    }

    public record PublicAuthConfiguration(String googleClientId) {}

    @GetMapping("/api/v1/auth/config")
    public PublicAuthConfiguration configuration() {
        return new PublicAuthConfiguration(googleClientId);
    }
}

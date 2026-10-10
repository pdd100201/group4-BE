package com.onlinelearning.config;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.onlinelearning.exception.ApiException;
import com.onlinelearning.api.GoogleIdentityService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.List;

@Component
public class GoogleIdentityVerifier implements GoogleIdentityService {
    private final GoogleIdTokenVerifier verifier;

    public GoogleIdentityVerifier(@Value("${app.google.client-id}") String clientId) {
        this.verifier = new GoogleIdTokenVerifier.Builder(new NetHttpTransport(), GsonFactory.getDefaultInstance())
                .setAudience(List.of(clientId))
                .build();
    }

    @Override public GoogleIdentity verify(String credential) {
        try {
            GoogleIdToken token = verifier.verify(credential);
            if (token == null || !Boolean.TRUE.equals(token.getPayload().getEmailVerified())) {
                throw invalidToken();
            }
            GoogleIdToken.Payload payload = token.getPayload();
            return new GoogleIdentity(payload.getSubject(), payload.getEmail(),
                    (String) payload.get("name"), (String) payload.get("picture"),
                    payload.getHostedDomain());
        } catch (GeneralSecurityException | IOException | IllegalArgumentException e) {
            throw invalidToken();
        }
    }

    private ApiException invalidToken() {
        return new ApiException(HttpStatus.UNAUTHORIZED, "Google credential is invalid or expired");
    }
}

package com.onlinelearning.service;

import com.onlinelearning.config.JwtTokenProvider;
import com.onlinelearning.entity.User;
import com.onlinelearning.exception.ApiException;
import com.onlinelearning.repository.AuthSessionRepository;
import com.onlinelearning.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;

@Service @RequiredArgsConstructor
public class ChatIdentityService {
    private final JwtTokenProvider jwt;
    private final AuthSessionRepository sessions;
    private final UserRepository users;

    @Transactional(readOnly = true)
    public Long tokenUser(String token) {
        if (token == null || !jwt.valid(token, JwtTokenProvider.ACCESS)) throw unauthorized();
        return sessions.findBySessionId(jwt.sessionId(token))
            .filter(session -> session.isActive(Instant.now()))
            .map(session -> session.getUser())
            .filter(user -> user.isEnabled() && !user.isBlocked() && user.getEmail().equals(jwt.subject(token)))
            .map(User::getId).orElseThrow(ChatIdentityService::unauthorized);
    }

    @Transactional(readOnly = true)
    public User current() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) throw unauthorized();
        return users.findByEmailIgnoreCase(authentication.getName())
            .filter(user -> user.isEnabled() && !user.isBlocked()).orElseThrow(ChatIdentityService::unauthorized);
    }

    private static ApiException unauthorized() {
        return new ApiException(HttpStatus.UNAUTHORIZED, "Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại.");
    }
}

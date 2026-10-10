package com.onlinelearning.config;

import com.onlinelearning.repository.AuthSessionRepository;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;
import java.time.Instant;

@Component @RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {
    private final JwtTokenProvider jwt;
    private final AuthSessionRepository sessions;

    @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                              FilterChain chain) throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            String token = header.substring(7);
            if (jwt.valid(token, JwtTokenProvider.ACCESS)) {
                sessions.findBySessionId(jwt.sessionId(token))
                        .filter(session -> session.isActive(Instant.now()))
                        .map(session -> session.getUser())
                        .filter(user -> user.isEnabled() && !user.isBlocked())
                        .ifPresent(user -> {
                            var authorities = user.getRoles().stream()
                                    .map(role -> new SimpleGrantedAuthority(role.authority())).toList();
                            SecurityContextHolder.getContext().setAuthentication(
                                    new UsernamePasswordAuthenticationToken(user.getEmail(), null, authorities));
                        });
            }
        }
        chain.doFilter(request, response);
    }
}

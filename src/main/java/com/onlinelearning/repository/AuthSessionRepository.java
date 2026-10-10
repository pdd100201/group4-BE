package com.onlinelearning.repository;

import com.onlinelearning.entity.AuthSession;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;

public interface AuthSessionRepository extends JpaRepository<AuthSession, Long> {
    Optional<AuthSession> findBySessionId(String sessionId);
    List<AuthSession> findByUserAndRevokedAtIsNull(com.onlinelearning.entity.User user);
}

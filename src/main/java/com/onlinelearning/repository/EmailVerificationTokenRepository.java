package com.onlinelearning.repository;

import com.onlinelearning.entity.EmailVerificationToken;
import com.onlinelearning.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface EmailVerificationTokenRepository extends JpaRepository<EmailVerificationToken, Long> {
    Optional<EmailVerificationToken> findByTokenHash(String tokenHash);
    void deleteByUserAndUsedAtIsNull(User user);
}

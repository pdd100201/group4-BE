package com.onlinelearning.repository;
import com.onlinelearning.entity.*; import org.springframework.data.jpa.repository.JpaRepository; import java.util.*;
public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken,Long> {
    Optional<PasswordResetToken> findByTokenHash(String tokenHash);
    void deleteByUserAndUsedAtIsNull(User user);
}

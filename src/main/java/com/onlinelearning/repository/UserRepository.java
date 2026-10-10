package com.onlinelearning.repository;
import com.onlinelearning.entity.User; import org.springframework.data.jpa.repository.JpaRepository; import java.util.*;
public interface UserRepository extends JpaRepository<User,Long> { Optional<User> findByEmailIgnoreCase(String email); Optional<User> findByGoogleSubject(String googleSubject); boolean existsByEmailIgnoreCase(String email); boolean existsByPhone(String phone); }

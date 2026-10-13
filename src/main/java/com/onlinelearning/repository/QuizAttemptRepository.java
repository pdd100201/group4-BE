package com.onlinelearning.repository;
import com.onlinelearning.entity.QuizAttempt;
import org.springframework.data.jpa.repository.JpaRepository;
public interface QuizAttemptRepository extends JpaRepository<QuizAttempt, Long> {
    boolean existsByQuizId(Long quizId);
}

package com.onlinelearning.repository;
import com.onlinelearning.entity.Quiz;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface QuizRepository extends JpaRepository<Quiz, Long> {
    List<Quiz> findByCourseIdOrderByIdAsc(Long courseId);
    Optional<Quiz> findByIdAndCourseId(Long id, Long courseId);
}

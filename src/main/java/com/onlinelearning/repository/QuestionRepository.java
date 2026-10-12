package com.onlinelearning.repository;
import com.onlinelearning.entity.Question;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface QuestionRepository extends JpaRepository<Question, Long> {
    Optional<Question> findByIdAndCourseId(Long id, Long courseId);
}

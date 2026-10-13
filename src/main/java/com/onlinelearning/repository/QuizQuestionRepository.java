package com.onlinelearning.repository;
import com.onlinelearning.entity.QuizQuestion;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.*;
public interface QuizQuestionRepository extends JpaRepository<QuizQuestion, Long> {
    List<QuizQuestion> findByQuizIdOrderByPositionAscIdAsc(Long quizId);
    @Modifying
    @Query("delete from QuizQuestion qq where qq.quiz.id = :quizId")
    void deleteByQuizId(@Param("quizId") Long quizId);
}

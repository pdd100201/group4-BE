package com.onlinelearning.repository;
import com.onlinelearning.entity.Lesson;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface LessonRepository extends JpaRepository<Lesson, Long> {
    List<Lesson> findByCourseIdOrderByPositionAscIdAsc(Long courseId);
    Optional<Lesson> findByIdAndCourseId(Long id, Long courseId);
    boolean existsByCourseIdAndPositionAndIdNot(Long courseId, Integer position, Long id);
}

package com.onlinelearning.repository;

import com.onlinelearning.entity.StudentLessonProgress;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;

public interface StudentLessonProgressRepository extends JpaRepository<StudentLessonProgress,Long> {
    List<StudentLessonProgress> findByStudentIdAndLessonCourseId(Long studentId,Long courseId);
    Optional<StudentLessonProgress> findByStudentIdAndLessonId(Long studentId,Long lessonId);
}

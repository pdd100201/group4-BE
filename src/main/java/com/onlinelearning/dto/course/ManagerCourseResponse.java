package com.onlinelearning.dto.course;

import com.onlinelearning.entity.*;
import java.math.BigDecimal;
import java.util.List;

public record ManagerCourseResponse(Long id, String title, String description, String category,
        BigDecimal price, CourseStatus status, String expertName, String rejectionReason, String reviewedBy,
        List<LessonPreview> lessons, List<QuizPreview> quizzes, String thumbnailUrl) {
    public record LessonPreview(Long id, String title, String content, Integer position, boolean active, Long videoMediaId) {}
    public record OptionPreview(Long id, String content, boolean correct) {}
    public record QuestionPreview(Long id, String content, String type, String explanation, Integer points, List<OptionPreview> options) {}
    public record QuizPreview(Long id, String title, Integer durationMinutes, Integer passRate, Integer maxAttempts,
                              boolean active, List<QuestionPreview> questions) {}
}

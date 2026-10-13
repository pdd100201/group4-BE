package com.onlinelearning.dto.quiz;

import com.onlinelearning.entity.CourseStatus;

public record QuizListItem(Long id, Long courseId, String courseTitle, String category, CourseStatus courseStatus,
    String title, long questionCount, Integer durationMinutes, Integer passRate, Integer maxAttempts,
    boolean active, boolean retained) {}

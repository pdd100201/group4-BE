package com.onlinelearning.dto.quiz;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.List;

public final class StudentQuizDtos {
    private StudentQuizDtos() {}
    public record History(Long id,Instant startedAt,Instant submittedAt,Double score,boolean passed) {}
    public record Info(Long id,Long courseId,Long lessonId,String courseTitle,String title,int durationMinutes,int passRate,int maxAttempts,int attemptsUsed,List<History> history) {}
    public record Option(Long id,String content) {}
    public record Question(Long id,int position,String content,String type,String imageUrl,int points,List<Option> options,List<Long> selectedIds) {}
    public record Attempt(Long id,Long quizId,Long courseId,Long lessonId,String title,Instant serverTime,Instant deadline,Instant submittedAt,List<Question> questions) {}
    public record Answer(@NotNull @Positive Long questionId,@NotNull @Size(max=4) List<@NotNull @Positive Long> optionIds) {}
    public record Submission(@NotNull @Size(max=500) @Valid List<Answer> answers) {}
    public record Review(Long id,int position,String content,String type,String imageUrl,int points,double awardedPoints,boolean correct,String explanation,List<Option> options,List<Long> selectedIds,List<Long> correctIds) {}
    public record Result(Long id,Long quizId,Long courseId,Long lessonId,String title,double score,boolean passed,int passRate,int correctQuestions,int totalQuestions,long secondsUsed,List<Review> questions) {}
}

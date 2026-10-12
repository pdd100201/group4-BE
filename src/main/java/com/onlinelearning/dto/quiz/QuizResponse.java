package com.onlinelearning.dto.quiz;
import com.onlinelearning.entity.Quiz;
import com.onlinelearning.entity.QuizQuestion;
import java.util.Comparator;
import java.util.List;
public record QuizResponse(
    Long id,
    Long courseId,
    Long lessonId,
    String title,
    Integer durationMinutes,
    Integer passRate,
    Integer maxAttempts,
    boolean active,
    List<QuizQuestionResponse> questions
) {
    public static QuizResponse from(Quiz q, List<QuizQuestion> items) {
        List<QuizQuestionResponse> mapped = items.stream()
            .sorted(Comparator.comparing(QuizQuestion::getPosition, Comparator.nullsLast(Integer::compareTo))
                .thenComparing(QuizQuestion::getId))
            .map(QuizQuestionResponse::from)
            .toList();
        return new QuizResponse(q.getId(), q.getCourse().getId(),
            q.getLesson() == null ? null : q.getLesson().getId(),
            q.getTitle(), q.getDurationMinutes(), q.getPassRate(), q.getMaxAttempts(), q.isActive(), mapped);
    }
}

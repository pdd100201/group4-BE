package com.onlinelearning.dto.quiz;
import com.onlinelearning.entity.QuizQuestion;
public record QuizQuestionResponse(Long questionId, String content, Integer position, Integer points) {
    public static QuizQuestionResponse from(QuizQuestion qq) {
        return new QuizQuestionResponse(qq.getQuestion().getId(), qq.getQuestion().getContent(), qq.getPosition(), qq.getPoints());
    }
}

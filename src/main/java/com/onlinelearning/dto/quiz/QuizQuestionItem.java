package com.onlinelearning.dto.quiz;
import jakarta.validation.constraints.*;
public record QuizQuestionItem(
    @NotNull(message="ID câu hỏi là bắt buộc") Long questionId,
    @NotNull(message="Thứ tự câu hỏi là bắt buộc") @Min(value=1, message="Thứ tự phải từ 1 trở lên") Integer position,
    @Min(value=1, message="Điểm phải từ 1 trở lên") Integer points
) {}

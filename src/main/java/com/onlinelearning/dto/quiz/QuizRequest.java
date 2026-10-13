package com.onlinelearning.dto.quiz;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.List;
public record QuizRequest(
    @NotBlank(message="Tên quiz là bắt buộc") @Size(max=255) String title,
    @Min(value=1, message="Thời lượng phải từ 1 phút trở lên") Integer durationMinutes,
    @Min(value=0, message="Tỷ lệ đạt không hợp lệ") @Max(value=100, message="Tỷ lệ đạt tối đa 100") Integer passRate,
    @Min(value=1, message="Số lần làm tối đa phải từ 1 trở lên") Integer maxAttempts,
    @NotNull(message="Trạng thái hoạt động là bắt buộc") Boolean active,
    Long lessonId,
    @Valid List<QuizQuestionItem> questions
) {}

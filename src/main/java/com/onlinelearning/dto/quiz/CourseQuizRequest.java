package com.onlinelearning.dto.quiz;

import com.onlinelearning.dto.question.QuestionRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.List;

/** The whole course assessment is saved in one transaction. */
public record CourseQuizRequest(
    @Positive Long id,
    @NotBlank @Size(max=255) String title,
    @NotNull @Min(1) Integer durationMinutes,
    @NotNull @Min(0) @Max(100) Integer passRate,
    @NotNull @Min(1) Integer maxAttempts,
    @NotEmpty @Size(max=200) List<@NotNull @Valid Item> questions
) {
    public record Item(@Positive Long questionId, @NotNull @Min(1) Integer points,
                       @NotNull @Valid QuestionRequest question) {}
}

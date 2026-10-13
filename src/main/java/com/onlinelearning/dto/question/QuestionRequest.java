package com.onlinelearning.dto.question;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.List;

public record QuestionRequest(@NotBlank @Size(max=4000) String content, @NotBlank String type,
    @Size(max=255) String explanation, @NotNull Boolean active, @Positive Long imageMediaId,
    @NotNull @Size(min=4,max=4) @Valid List<Option> options) {
    public record Option(@NotBlank @Size(max=255) String content, boolean correct) {}
}

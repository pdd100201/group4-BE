package com.onlinelearning.dto.course;
import jakarta.validation.constraints.*;
public record LessonRequest(
    @NotBlank(message="Tên bài học là bắt buộc") @Size(max=255) String title,
    @NotBlank(message="Nội dung bài học là bắt buộc") @Size(max=8000) String content,
    @NotNull @Min(value=1, message="Thứ tự phải từ 1 trở lên") Integer position,
    @NotNull Boolean active,
    Long videoMediaId
) {
    public LessonRequest(String title, String content, Integer position, Boolean active) { this(title, content, position, active, null); }
}

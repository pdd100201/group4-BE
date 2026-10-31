package com.onlinelearning.dto.course;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;

public record CourseRequest(
    @NotBlank(message="Tên khóa học là bắt buộc") @Size(max=30, message="Tên khóa học tối đa 30 ký tự") String title,
    @NotBlank(message="Mô tả là bắt buộc") @Size(max=255, message="Mô tả tối đa 255 ký tự") String description,
    @NotBlank(message="Danh mục là bắt buộc") @Size(max=30, message="Danh mục tối đa 30 ký tự") String category,
    @NotNull @DecimalMin(value="0", message="Giá không được âm") @Digits(integer=10, fraction=2) BigDecimal price,
    Long thumbnailMediaId
) {
    public CourseRequest(String title, String description, String category, BigDecimal price) { this(title, description, category, price, null); }
}

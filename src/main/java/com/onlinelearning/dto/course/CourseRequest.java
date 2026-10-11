package com.onlinelearning.dto.course;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;

public record CourseRequest(
    @NotBlank(message="Tên khóa học là bắt buộc") @Size(max=255) String title,
    @NotBlank(message="Mô tả là bắt buộc") @Size(max=4000) String description,
    @NotBlank(message="Danh mục là bắt buộc") @Size(max=255) String category,
    @NotNull @DecimalMin(value="0", message="Giá không được âm") @Digits(integer=10, fraction=2) BigDecimal price,
    Long thumbnailMediaId
) {
    public CourseRequest(String title, String description, String category, BigDecimal price) { this(title, description, category, price, null); }
}

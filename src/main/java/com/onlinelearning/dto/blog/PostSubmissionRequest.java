package com.onlinelearning.dto.blog;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record PostSubmissionRequest(
    @NotBlank(message="Tiêu đề không được trống.") @Size(max=255) String title,
    @NotBlank(message="Danh mục không được trống.") @Size(max=255) String category,
    @NotBlank(message="Tóm tắt không được trống.") @Size(max=1000) String summary,
    @NotBlank(message="Nội dung không được trống.") @Size(max=12000) String content,
    @Positive Long thumbnailMediaId) {}

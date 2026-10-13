package com.onlinelearning.dto.blog;

import jakarta.validation.constraints.*;

public record PostRequest(
    @NotBlank(message="Tiêu đề không được trống.") @Size(max=255) String title,
    @Size(max=255) String category,
    @Size(max=1000) String summary,
    @NotBlank(message="Nội dung không được trống.") @Size(max=12000) String content,
    @Positive Long thumbnailMediaId,
    @NotNull(message="Cần chọn trạng thái xuất bản.") Boolean published) {}

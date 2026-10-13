package com.onlinelearning.dto.blog;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record CommentStatusRequest(@NotBlank @Pattern(regexp="VISIBLE|HIDDEN|DISABLED", message="Trạng thái bình luận không hợp lệ.") String status) {}

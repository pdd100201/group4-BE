package com.onlinelearning.dto.blog;

import jakarta.validation.constraints.*;

public record CommentRequest(@NotBlank(message="Bình luận không được trống.") @Size(max=2000, message="Bình luận tối đa 2000 ký tự.") String content) {}

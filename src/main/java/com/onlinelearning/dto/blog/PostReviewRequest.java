package com.onlinelearning.dto.blog;

import jakarta.validation.constraints.NotNull;

public record PostReviewRequest(@NotNull Decision decision) {
    public enum Decision { APPROVE, REJECT }
}

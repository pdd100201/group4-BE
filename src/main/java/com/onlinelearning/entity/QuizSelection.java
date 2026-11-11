package com.onlinelearning.entity;

import java.time.Instant;

/** An answer owns its selections. Legacy link IDs/timestamps survive migration. */
public record QuizSelection(Long legacyId, Long questionOptionId, Instant createdAt, Instant updatedAt) {}

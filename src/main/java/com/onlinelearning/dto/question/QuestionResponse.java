package com.onlinelearning.dto.question;

import com.onlinelearning.entity.Question;
import java.util.List;

public record QuestionResponse(Long id, Long courseId, String content, String type, String explanation,
    boolean active, Long imageMediaId, String imageUrl, List<Option> options) {
    public record Option(Long id, String content, boolean correct) {}
    public static QuestionResponse from(Question q) {
        return new QuestionResponse(q.getId(), q.getCourse().getId(), q.getContent(), q.getType(), q.getExplanation(),
            q.isActive(), q.getImage() == null ? null : q.getImage().getId(), q.getImage() == null ? null : q.getImage().getSecureUrl(),
            q.getOptions().stream().sorted(java.util.Comparator.comparing(o -> o.getId()))
                .map(o -> new Option(o.getId(), o.getContent(), o.isCorrect())).toList());
    }
}

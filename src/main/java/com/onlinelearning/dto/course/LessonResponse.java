package com.onlinelearning.dto.course;
import com.onlinelearning.entity.Lesson;
public record LessonResponse(Long id, String title, String content, Integer position, boolean active, Long videoMediaId) {
    public static LessonResponse from(Lesson l) { return new LessonResponse(l.getId(), l.getTitle(), l.getContent(), l.getPosition(), l.isActive(), l.getVideo() == null ? null : l.getVideo().getId()); }
}

package com.onlinelearning.service;

import com.onlinelearning.entity.*;
import jakarta.persistence.EntityManager;
import java.time.Instant;

/** Isolated test fixtures only; never inserted into the user's database. */
final class ReadyCourseFixture {
    static void populate(EntityManager em, Course course) {
        MediaAsset image = asset(em, course, "image", "COURSE_IMAGE"); course.setThumbnail(image);
        Lesson lesson = new Lesson(); lesson.setCourse(course); lesson.setTitle("Bài một"); lesson.setContent("Lesson content");
        lesson.setPosition(1); lesson.setActive(true); lesson.setVideo(asset(em, course, "video", "LESSON_VIDEO")); em.persist(lesson);
        Quiz quiz = new Quiz(); quiz.setCourse(course); quiz.setTitle("Quiz"); quiz.setDurationMinutes(15); quiz.setPassRate(70); quiz.setMaxAttempts(3); quiz.setActive(true); em.persist(quiz);
    }
    private static MediaAsset asset(EntityManager em, Course course, String resource, String purpose) {
        MediaAsset a = new MediaAsset(); a.setOwner(course.getExpert()); a.setCourse(course); a.setPublicId("test/fixture");
        a.setPurpose(purpose); a.setResourceType(resource); a.setDeliveryType("video".equals(resource) ? "authenticated" : "upload");
        a.setFormat("video".equals(resource) ? "mp4" : "png"); a.setSecureUrl("https://example.invalid/test-fixture"); a.setCreatedAt(Instant.now()); em.persist(a); return a;
    }
}

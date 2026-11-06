package com.onlinelearning.service;

import com.onlinelearning.entity.*;
import jakarta.persistence.EntityManager;
import java.time.Instant;

/** Isolated test fixtures only; never inserted into the user's database. */
final class ReadyCourseFixture {
    static void populate(EntityManager em, Course course) {
        MediaAsset image = asset(em, course, "image", "COURSE_IMAGE"); course.setThumbnail(image);
        CoursePackage p=new CoursePackage();p.setCourse(course);p.setDuration(PackageDuration.ONE_MONTH);p.setPrice(java.math.BigDecimal.valueOf(100000));em.persist(p);
        Lesson lesson = new Lesson(); lesson.setCourse(course); lesson.setTitle("Bài một"); lesson.setContent("Lesson content");
        lesson.setPosition(1); lesson.setActive(true); lesson.setVideo(asset(em, course, "video", "LESSON_VIDEO")); em.persist(lesson);
        Quiz quiz = new Quiz(); quiz.setCourse(course); quiz.setTitle("Quiz"); quiz.setDurationMinutes(15); quiz.setPassRate(70); quiz.setMaxAttempts(3); quiz.setActive(true); em.persist(quiz);
        Question question=new Question();question.setCourse(course);question.setContent("Question");question.setType("SINGLE_CHOICE");em.persist(question);
        for(int i=0;i<4;i++){QuestionOption option=new QuestionOption();option.setQuestion(question);option.setContent("Option "+i);option.setCorrect(i==0);question.getOptions().add(option);em.persist(option);}
        QuizQuestion link=new QuizQuestion();link.setQuiz(quiz);link.setQuestion(question);link.setPosition(1);link.setPoints(1);em.persist(link);
    }
    private static MediaAsset asset(EntityManager em, Course course, String resource, String purpose) {
        MediaAsset a = new MediaAsset(); a.setOwner(course.getExpert()); a.setCourse(course); a.setPublicId("test/fixture");
        a.setPurpose(purpose); a.setResourceType(resource); a.setDeliveryType("video".equals(resource) ? "authenticated" : "upload");
        a.setFormat("video".equals(resource) ? "mp4" : "png"); a.setSecureUrl("https://example.invalid/test-fixture"); a.setCreatedAt(Instant.now()); em.persist(a); return a;
    }
}

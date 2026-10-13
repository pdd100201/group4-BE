package com.onlinelearning.service;

import com.onlinelearning.dto.course.CourseRequest;
import com.onlinelearning.entity.*;
import com.onlinelearning.exception.ApiException;
import com.onlinelearning.repository.*;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.test.web.servlet.MockMvc;
import java.math.BigDecimal;
import java.time.Instant;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest @ActiveProfiles("test") @Transactional @AutoConfigureMockMvc
class StudentLessonIntegrationTests {
    @Autowired StudentLessonService learning;
    @Autowired ExpertCourseService courses;
    @Autowired CourseRepository repository;
    @Autowired UserRepository users;
    @Autowired CourseRegistrationRepository registrations;
    @Autowired StudentLessonProgressRepository progress;
    @Autowired EntityManager em;
    @Autowired MockMvc mvc;
    static final String STUDENT="student@onlinelearning.local";
    record Fixture(Long courseId,Long first,Long second,Long hidden,CourseRegistration registration) {}
    Fixture fixture() {
        Long id=courses.create("expert@onlinelearning.local",new CourseRequest("Learning course","Description","Java",BigDecimal.ZERO)).id();
        Course course=repository.findById(id).orElseThrow();course.setStatus(CourseStatus.PUBLISHED);
        var first=new Lesson();first.setCourse(course);first.setTitle("First");first.setContent("Protected lesson content");first.setPosition(1);em.persist(first);
        var second=new Lesson();second.setCourse(course);second.setTitle("Second");second.setPosition(2);em.persist(second);
        var hidden=new Lesson();hidden.setCourse(course);hidden.setTitle("Hidden");hidden.setPosition(3);hidden.setActive(false);em.persist(hidden);
        var r=new CourseRegistration();r.setCourse(course);r.setStudent(users.findByEmailIgnoreCase(STUDENT).orElseThrow());r.setStatus(RegistrationStatus.ACTIVE);r.setValidFrom(Instant.now().minusSeconds(60));r.setValidUntil(Instant.now().plusSeconds(3600));r.setLifetimeSnapshot(false);registrations.saveAndFlush(r);
        return new Fixture(id,first.getId(),second.getId(),hidden.getId(),r);
    }
    @Test void activeStudentReadsOnlyAvailableLessonsAndCanResume() {
        var f=fixture();var read=learning.read(f.courseId(),null,STUDENT);assertEquals(2,read.lessons().size());assertEquals(f.first(),read.currentLesson().id());assertEquals("Protected lesson content",read.currentLesson().content());
        assertEquals(f.second(),learning.read(f.courseId(),f.second(),STUDENT).currentLesson().id());
        assertEquals(404,assertThrows(ApiException.class,()->learning.read(f.courseId(),f.hidden(),STUDENT)).status().value());
    }
    @Test void completedProgressIsIdempotentAndChoosesNextLesson() {
        var f=fixture();var completed=learning.complete(f.courseId(),f.first(),STUDENT);assertEquals(50,completed.progressPercent());assertEquals(1,completed.completedLessons());
        learning.complete(f.courseId(),f.first(),STUDENT);assertEquals(1,progress.findByStudentIdAndLessonCourseId(f.registration().getStudent().getId(),f.courseId()).size());
        assertEquals(f.second(),learning.read(f.courseId(),null,STUDENT).currentLesson().id());
    }
    @Test void expiredCancelledAndUnpublishedAccessIsDenied() {
        var f=fixture();f.registration().setValidUntil(Instant.now().minusSeconds(1));assertEquals(403,assertThrows(ApiException.class,()->learning.read(f.courseId(),null,STUDENT)).status().value());
        f.registration().setValidUntil(Instant.now().plusSeconds(3600));f.registration().setStatus(RegistrationStatus.CANCELLED);assertThrows(ApiException.class,()->learning.complete(f.courseId(),f.first(),STUDENT));
        f.registration().setStatus(RegistrationStatus.ACTIVE);repository.findById(f.courseId()).orElseThrow().setStatus(CourseStatus.ARCHIVED);assertEquals(404,assertThrows(ApiException.class,()->learning.read(f.courseId(),null,STUDENT)).status().value());
    }
    @Test void foreignCourseAndWrongRoleCannotReadOrComplete() throws Exception {
        var f=fixture();var other=fixture();assertEquals(404,assertThrows(ApiException.class,()->learning.read(other.courseId(),f.first(),STUDENT)).status().value());
        String path="/api/v1/student/courses/"+f.courseId()+"/learning";
        mvc.perform(get(path)).andExpect(status().isUnauthorized());
        mvc.perform(get(path).with(user("expert@onlinelearning.local").roles("EXPERT"))).andExpect(status().isForbidden());
        mvc.perform(get(path).with(user(STUDENT).roles("STUDENT"))).andExpect(status().isOk()).andExpect(jsonPath("currentLesson.content").value("Protected lesson content"));
    }
}

package com.onlinelearning.service;

import com.onlinelearning.dto.course.CourseRequest;
import com.onlinelearning.entity.*;
import com.onlinelearning.repository.CourseRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest @ActiveProfiles("test") @AutoConfigureMockMvc @Transactional
class PublicCourseIntegrationTests {
    @Autowired ExpertCourseService expert;
    @Autowired CourseRepository courses;
    @Autowired EntityManager em;
    @Autowired MockMvc mvc;
    private Course create(String title, CourseStatus status) {
        Long id = expert.create("expert@onlinelearning.local", new CourseRequest(title, "Description " + title, "Programming", new BigDecimal("299000"))).id();
        Course course = courses.findById(id).orElseThrow(); course.setStatus(status); return course;
    }
    @Test void detailMatchesRequestedCourseAndOnlyExposesActiveOutline() throws Exception {
        Course first = create("First course", CourseStatus.PUBLISHED);
        Course second = create("Second course", CourseStatus.PUBLISHED);
        for (int position : new int[]{2,1,3}) {
            Lesson lesson = new Lesson(); lesson.setCourse(second); lesson.setTitle("Lesson " + position);
            lesson.setContent("PRIVATE LESSON CONTENT"); lesson.setPosition(position); lesson.setActive(position != 3); em.persist(lesson);
        }
        Quiz quiz = new Quiz(); quiz.setCourse(second); quiz.setTitle("Private quiz"); em.persist(quiz);
        Quiz hidden = new Quiz(); hidden.setCourse(second); hidden.setTitle("Hidden quiz"); hidden.setActive(false); em.persist(hidden);
        mvc.perform(get("/api/v1/public/courses/" + second.getId()))
            .andExpect(status().isOk()).andExpect(jsonPath("id").value(second.getId().intValue()))
            .andExpect(jsonPath("title").value("Second course")).andExpect(jsonPath("description").value("Description Second course"))
            .andExpect(jsonPath("price").value(299000)).andExpect(jsonPath("expertName").value("Online Learning Expert"))
            .andExpect(jsonPath("lessons.length()").value(2)).andExpect(jsonPath("lessons[0].title").value("Lesson 1"))
            .andExpect(jsonPath("lessons[0].content").doesNotExist()).andExpect(jsonPath("lessons[0].videoMediaId").doesNotExist())
            .andExpect(jsonPath("quizCount").value(1)).andExpect(jsonPath("quizzes").doesNotExist())
            .andExpect(jsonPath("expert.email").doesNotExist());
        mvc.perform(get("/api/v1/public/courses/" + first.getId())).andExpect(status().isOk())
            .andExpect(jsonPath("title").value("First course")).andExpect(jsonPath("lessons.length()").value(0));
    }
    @Test void nonPublishedAndMissingCoursesAreNotExposedEvenByDirectUrl() throws Exception {
        for (CourseStatus state : CourseStatus.values()) {
            if (state == CourseStatus.PUBLISHED) continue;
            Course course = create("Private " + state, state);
            mvc.perform(get("/api/v1/public/courses/" + course.getId())).andExpect(status().isNotFound());
        }
        mvc.perform(get("/api/v1/public/courses")).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
        mvc.perform(get("/api/v1/public/courses/" + Long.MAX_VALUE)).andExpect(status().isNotFound());
        for (String id : new String[]{"0", "-1", "invalid"}) mvc.perform(get("/api/v1/public/courses/" + id)).andExpect(status().isBadRequest());
    }
    @Test void listAndDetailReflectExpertUpdatesAndArchive() throws Exception {
        Course course = create("Original", CourseStatus.DRAFT);
        expert.update(course.getId(), "expert@onlinelearning.local", new CourseRequest("Expert edited title", "New description", "Java", new BigDecimal("450000")));
        course.setStatus(CourseStatus.PUBLISHED);
        mvc.perform(get("/api/v1/public/courses")).andExpect(status().isOk())
            .andExpect(jsonPath("$[0].id").value(course.getId().intValue())).andExpect(jsonPath("$[0].title").value("Expert edited title"));
        mvc.perform(get("/api/v1/public/courses/" + course.getId())).andExpect(status().isOk())
            .andExpect(jsonPath("price").value(450000)).andExpect(jsonPath("description").value("New description"));
        course.setStatus(CourseStatus.ARCHIVED);
        mvc.perform(get("/api/v1/public/courses/" + course.getId())).andExpect(status().isNotFound());
    }

    @Test void homeSearchAndCategoryLinksFilterPublicCoursesInDatabase() throws Exception {
        Course java = create("Java basic", CourseStatus.PUBLISHED);
        java.setCategory("Code");
        Course design = create("Design basic", CourseStatus.PUBLISHED);
        design.setCategory("Design");
        create("Java hidden", CourseStatus.DRAFT);
        mvc.perform(get("/api/v1/public/courses").param("search", "java").param("category", "Code"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1)).andExpect(jsonPath("$[0].id").value(java.getId().intValue()));
        mvc.perform(get("/api/v1/public/courses").param("search", "%"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
    }
}

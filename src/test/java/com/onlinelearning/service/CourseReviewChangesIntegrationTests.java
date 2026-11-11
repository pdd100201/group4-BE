package com.onlinelearning.service;

import com.onlinelearning.dto.course.CourseRequest;
import com.onlinelearning.entity.*;
import com.onlinelearning.repository.*;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest @ActiveProfiles("test") @AutoConfigureMockMvc @Transactional
class CourseReviewChangesIntegrationTests {
 @Autowired ExpertCourseService expert;
 @Autowired CourseReviewChangesService changes;
 @Autowired CourseRepository courses;
 @Autowired EntityManager em;
 @Autowired MockMvc mvc;
 private static final String OWNER="expert@onlinelearning.local";
 Course published(){
  var created=expert.create(OWNER,new CourseRequest("Original title","Old description","Java",BigDecimal.valueOf(100000)));
  var c=courses.findById(created.id()).orElseThrow();ReadyCourseFixture.populate(em,c);c.setStatus(CourseStatus.PUBLISHED);em.flush();return c;
 }
 CourseRequest input(Course c,String title){return new CourseRequest(title,"New description","Java",BigDecimal.valueOf(200000),c.getThumbnail().getId());}
 @Test void keepsOriginalValuesAcrossMultipleEditsAndJsonRoundTrip() {
  var c=published();Long id=c.getId();
  expert.update(id,OWNER,input(c,"First edit"));expert.update(id,OWNER,input(c,"Final edit"));
  em.flush();em.clear();
  var result=changes.compare(id);
  assertThat(result.baselineAvailable()).isTrue();
  assertThat(result.changes()).anySatisfy(change->{assertThat(change.key()).isEqualTo("course.title");assertThat(change.before()).isEqualTo("Original title");assertThat(change.after()).isEqualTo("Final edit");});
  assertThat(result.changes()).noneMatch(change->change.key().equals("course.category"));
 }
 @Test void showsLongLessonChangesAddedAndRemovedItemsAndChangedPackages() {
  var c=published();Long id=c.getId();
  var lesson=em.createQuery("select l from Lesson l where l.course.id=:id",Lesson.class).setParameter("id",id).getSingleResult();
  String oldText="Nội dung cũ ".repeat(400);lesson.setContent(oldText);em.flush();
  expert.update(id,OWNER,input(c,"Updated"));
  lesson.setContent("Nội dung mới");
  var p=em.createQuery("select p from CoursePackage p where p.course.id=:id",CoursePackage.class).setParameter("id",id).getSingleResult();p.setPrice(BigDecimal.valueOf(300000));
  var added=new Lesson();added.setCourse(c);added.setTitle("Added lesson");added.setContent("New");added.setPosition(2);em.persist(added);
  em.flush();em.clear();
  var result=changes.compare(id);
  assertThat(result.changes()).anySatisfy(change->{assertThat(change.key()).isEqualTo("lesson."+lesson.getId()+".content");assertThat(change.before()).isEqualTo(oldText);assertThat(change.after()).isEqualTo("Nội dung mới");});
  assertThat(result.changes()).anySatisfy(change->{assertThat(change.key()).isEqualTo("package.ONE_MONTH.price");assertThat(change.before()).isEqualTo("100000");assertThat(change.after()).isEqualTo("300000");});
  assertThat(result.changes()).anyMatch(change->change.before()==null&&"Added lesson".equals(change.after()));
  em.remove(em.find(Lesson.class,added.getId()));
  assertThat(changes.compare(id).changes()).noneMatch(change->change.key().startsWith("lesson."+added.getId()+"."));
 }
 @Test void nextPublishedEditStartsANewBaseline() {
  var c=published();expert.update(c.getId(),OWNER,input(c,"First approved revision"));
  c.setStatus(CourseStatus.PUBLISHED);
  expert.update(c.getId(),OWNER,input(c,"Second revision"));
  assertThat(changes.compare(c.getId()).changes()).anySatisfy(change->{assertThat(change.key()).isEqualTo("course.title");assertThat(change.before()).isEqualTo("First approved revision");assertThat(change.after()).isEqualTo("Second revision");});
 }
 @Test void legacyCoursesDoNotInventOldValuesAndEndpointRequiresManager() throws Exception {
  var c=published();assertThat(changes.compare(c.getId()).baselineAvailable()).isFalse();
  mvc.perform(get("/api/v1/manager/courses/"+c.getId()+"/changes")).andExpect(status().isUnauthorized());
  mvc.perform(get("/api/v1/manager/courses/"+c.getId()+"/changes").with(user(OWNER).roles("EXPERT"))).andExpect(status().isForbidden());
  mvc.perform(get("/api/v1/manager/courses/"+c.getId()+"/changes").with(user("manager@onlinelearning.local").roles("MANAGER"))).andExpect(status().isOk()).andExpect(jsonPath("baselineAvailable").value(false));
 }
}

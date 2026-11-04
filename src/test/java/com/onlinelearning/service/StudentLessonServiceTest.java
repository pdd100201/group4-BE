package com.onlinelearning.service;

import com.onlinelearning.entity.*;
import com.onlinelearning.repository.*;
import com.onlinelearning.exception.ApiException;
import jakarta.persistence.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.data.domain.Pageable;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

@ExtendWith(MockitoExtension.class)
class StudentLessonServiceTest {
    @Mock UserRepository users;
    @Mock CourseRepository courses;
    @Mock LessonRepository lessons;
    @Mock CourseRegistrationRepository registrations;
    @Mock StudentLessonProgressRepository progress;
    @Mock EntityManager em;
    @Mock QuizRepository quizzes;
    @Mock CertificateService certificateService;
    @InjectMocks StudentLessonService service;
    User student;
    Course course;
    Lesson first, second, hidden;
    static final String EMAIL="student@test.local";
    @BeforeEach void fixture() {
        student=new User(); student.setId(4L); student.setEnabled(true); Role role=new Role(); role.setCode("STUDENT"); student.setRoles(Set.of(role));
        User expert=new User(); expert.setFullName("Expert"); course=new Course(); course.setId(1L); course.setTitle("Java"); course.setStatus(CourseStatus.PUBLISHED); course.setExpert(expert);
        first=lesson(7L,true); second=lesson(8L,true); hidden=lesson(9L,false);
    }
    Lesson lesson(long id,boolean active) { Lesson l=new Lesson(); l.setId(id); l.setCourse(course); l.setTitle("Lesson "+id); l.setContent("Content"); l.setPosition((int)id); l.setActive(active); return l; }
    void account() { when(users.findByEmailIgnoreCase(EMAIL)).thenReturn(Optional.of(student)); }
    void access() { account(); when(courses.findById(1L)).thenReturn(Optional.of(course)); when(registrations.countCurrentAccess(eq(4L),eq(1L),any())).thenReturn(1L); }
    void outline() { when(lessons.findByCourseIdOrderByPositionAscIdAsc(1L)).thenReturn(List.of(first,second,hidden)); }
    StudentLessonProgress completed(Lesson lesson) { var p=new StudentLessonProgress(); p.setStudent(student); p.setLesson(lesson); p.setCompleted(true); return p; }
    void error(HttpStatus status,org.junit.jupiter.api.function.Executable action) { assertEquals(status,assertThrows(ApiException.class,action).status()); }
    @Test void missingAccount() { error(HttpStatus.UNAUTHORIZED,()->service.read(1L,null,EMAIL)); verifyNoInteractions(courses,lessons,progress); }
    @Test void disabledStudent() { account(); student.setEnabled(false); error(HttpStatus.FORBIDDEN,()->service.read(1L,null,EMAIL)); verifyNoInteractions(courses); }
    @Test void blockedStudent() { account(); student.setBlocked(true); error(HttpStatus.FORBIDDEN,()->service.read(1L,null,EMAIL)); verifyNoInteractions(courses); }
    @Test void wrongRole() { account(); student.setRoles(Set.of()); error(HttpStatus.FORBIDDEN,()->service.read(1L,null,EMAIL)); verifyNoInteractions(courses); }
    @Test void courseNotFound() { account(); error(HttpStatus.NOT_FOUND,()->service.read(1L,null,EMAIL)); verifyNoInteractions(registrations,lessons); }
    @ParameterizedTest @EnumSource(value=CourseStatus.class,names="PUBLISHED",mode=EnumSource.Mode.EXCLUDE)
    void unpublishedCourseUnavailable(CourseStatus status) { account(); course.setStatus(status); when(courses.findById(1L)).thenReturn(Optional.of(course)); error(HttpStatus.NOT_FOUND,()->service.read(1L,null,EMAIL)); verifyNoInteractions(registrations); }
    @Test void expiredOrMissingRegistration() { account(); when(courses.findById(1L)).thenReturn(Optional.of(course)); error(HttpStatus.FORBIDDEN,()->service.read(1L,null,EMAIL)); verifyNoInteractions(lessons,progress); }
    @Test void readResumesFirstIncompleteAndIgnoresHiddenProgress() {
        access(); outline(); when(progress.findByStudentIdAndLessonCourseId(4L,1L)).thenReturn(List.of(completed(first),completed(hidden)));
        var result=service.read(1L,null,EMAIL); assertEquals(2,result.lessons().size()); assertEquals(1,result.completedLessons()); assertEquals(50,result.progressPercent()); assertEquals(8L,result.currentLesson().id());
        verify(progress,never()).saveAndFlush(any());
    }
    @Test void emptyOutlineHasNoCurrentLessonOrProgress() { access(); var result=service.read(1L,null,EMAIL); assertNull(result.currentLesson()); assertEquals(0,result.progressPercent()); assertTrue(result.lessons().isEmpty()); }
    @Test void allCompletedReturnsFirstLesson() { access(); outline(); when(progress.findByStudentIdAndLessonCourseId(4L,1L)).thenReturn(List.of(completed(first),completed(second))); var result=service.read(1L,null,EMAIL); assertEquals(100,result.progressPercent()); assertEquals(7L,result.currentLesson().id()); }
    @Test void explicitLessonReturnsVideo() { access(); outline(); MediaAsset video=new MediaAsset(); video.setId(20L); second.setVideo(video); assertEquals(20L,service.read(1L,8L,EMAIL).currentLesson().videoMediaId()); }
    @Test void hiddenLessonCannotBeRead() { access(); outline(); error(HttpStatus.NOT_FOUND,()->service.read(1L,9L,EMAIL)); }
    @Test void foreignOrMissingLessonCannotBeRead() { access(); outline(); error(HttpStatus.NOT_FOUND,()->service.read(1L,999L,EMAIL)); }
    @Test void completeCreatesRegistrationLinkedProgress() {
        access(); outline(); when(lessons.findByIdAndCourseId(7L,1L)).thenReturn(Optional.of(first));
        CourseRegistration registration=new CourseRegistration(); registration.setId(50L);
        when(registrations.findAccessRegistration(eq(4L),eq(1L),any(),any(Pageable.class))).thenReturn(List.of(registration));
        service.complete(1L,7L,EMAIL);
        ArgumentCaptor<StudentLessonProgress> capture=ArgumentCaptor.forClass(StudentLessonProgress.class); verify(progress).saveAndFlush(capture.capture()); var saved=capture.getValue();
        assertSame(first,saved.getLesson()); assertSame(student,saved.getStudent()); assertSame(registration,saved.getRegistration()); assertTrue(saved.isCompleted()); assertEquals("COMPLETED",saved.getStatus()); assertEquals(100,saved.getProgressPercent()); assertNotNull(saved.getCompletedAt()); verify(em).lock(student,LockModeType.PESSIMISTIC_WRITE);
    }
    @Test void repeatedCompletionDoesNotSaveAgain() {
        access(); outline(); when(lessons.findByIdAndCourseId(7L,1L)).thenReturn(Optional.of(first)); when(progress.findByStudentIdAndLessonId(4L,7L)).thenReturn(Optional.of(completed(first)));
        service.complete(1L,7L,EMAIL); verify(progress,never()).saveAndFlush(any()); verify(registrations,never()).findAccessRegistration(anyLong(),anyLong(),any(),any());
    }
    @Test void completingHiddenLessonRejected() { access(); when(lessons.findByIdAndCourseId(9L,1L)).thenReturn(Optional.of(hidden)); error(HttpStatus.NOT_FOUND,()->service.complete(1L,9L,EMAIL)); verifyNoInteractions(progress); }
    @Test void accessExpiresBeforeProgressSave() { access(); when(lessons.findByIdAndCourseId(7L,1L)).thenReturn(Optional.of(first)); error(HttpStatus.FORBIDDEN,()->service.complete(1L,7L,EMAIL)); verify(progress,never()).saveAndFlush(any()); }
}

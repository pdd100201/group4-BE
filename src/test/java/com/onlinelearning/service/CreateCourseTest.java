package com.onlinelearning.service;

import com.onlinelearning.dto.course.CourseRequest;
import com.onlinelearning.entity.*;
import com.onlinelearning.exception.ApiException;
import com.onlinelearning.repository.*;
import jakarta.persistence.EntityManager;
import jakarta.validation.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.util.Optional;

import static com.onlinelearning.support.CreateCourseFixtures.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** One test per UTCID column in the user's Create Course sheet. No live DB/server. */
@ExtendWith(MockitoExtension.class)
class CreateCourseTest {
    @Mock CourseRepository courses;
    @Mock UserRepository users;
    @Mock AuditLogRepository auditLogs;
    @Mock EntityManager entityManager;
    @Mock MediaService media;
    @InjectMocks ExpertCourseService service;

    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void setupValidator() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void closeValidator() {
        factory.close();
    }

    @Test
    @DisplayName("UTCID01 - Normal: DB Expert, sample title, description, Programming, 299000 and image")
    void UTCID01_createCourseWithSheetInput_returnsDraftAndRecordsAudit() {
        User owner = expert();
        CourseRequest input = request();
        assertTrue(validator.validate(input).isEmpty());
        when(users.findByEmailIgnoreCase(EMAIL)).thenReturn(Optional.of(owner));
        // Simulate the ID assigned on INSERT; never update the existing source course (id=1).
        long newCourseId = 100L;
        when(courses.save(any(Course.class))).thenAnswer(invocation -> {
            Course course = invocation.getArgument(0);
            assertNull(course.getId());
            course.setId(newCourseId);
            return course;
        });
        MediaAsset image = new MediaAsset();
        image.setId(IMAGE_ID);
        image.setOwner(owner);
        image.setPurpose("COURSE_IMAGE");
        image.setResourceType("image");
        when(media.bindNewCourseThumbnail(eq(IMAGE_ID), any(Course.class))).thenReturn(image);

        var result = service.create(EMAIL, input);

        assertEquals(newCourseId, result.id());
        assertEquals(CourseStatus.DRAFT, result.status());
        assertEquals(TITLE, result.title());
        assertEquals(DESCRIPTION, result.description());
        assertEquals(CATEGORY, result.category());
        assertEquals(PRICE, result.price());
        assertEquals(IMAGE_ID, result.thumbnailMediaId());
        ArgumentCaptor<Course> saved = ArgumentCaptor.forClass(Course.class);
        verify(courses).save(saved.capture());
        assertSame(owner, saved.getValue().getExpert());
        assertEquals(EXPERT_ID, saved.getValue().getExpert().getId());
        assertSame(image, saved.getValue().getThumbnail());
        verify(media).bindNewCourseThumbnail(eq(IMAGE_ID), any(Course.class));
        ArgumentCaptor<AuditLog> audit = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogs).save(audit.capture());
        assertEquals("COURSE_CREATED", audit.getValue().getAction());
        assertEquals("COURSE", audit.getValue().getTargetType());
        assertEquals(Long.toString(newCourseId), audit.getValue().getTargetId());
        assertSame(owner, audit.getValue().getActor());
        assertNotNull(audit.getValue().getOccurredAt());
        verifyNoInteractions(entityManager);
    }

    @Test
    @DisplayName("UTCID02 - Boundary: title = 30 characters")
    void UTCID02_title30Characters_hasNoValidationError() {
        String title = atLength(TITLE, 30);
        assertEquals(30, title.length());
        assertValid(request(title, DESCRIPTION, CATEGORY));
    }

    @Test
    @DisplayName("UTCID03 - Boundary: title = 31 characters")
    void UTCID03_title31Characters_hasExactlyOneTitleError() {
        String title = atLength(TITLE, 31);
        assertEquals(31, title.length());
        assertInvalid(request(title, DESCRIPTION, CATEGORY), "title", "Tên khóa học tối đa 30 ký tự");
    }

    @Test
    @DisplayName("UTCID04 - Boundary: description = 255 characters (sheet: <=255)")
    void UTCID04_description255Characters_hasNoValidationError() {
        String description = atLength(DESCRIPTION, 255);
        assertEquals(255, description.length());
        assertValid(request(TITLE, description, CATEGORY));
    }

    @Test
    @DisplayName("UTCID05 - Boundary: description = 257 characters (sheet: >256)")
    void UTCID05_descriptionOver256Characters_hasExactlyOneDescriptionError() {
        String description = atLength(DESCRIPTION, 257);
        assertEquals(257, description.length());
        assertInvalid(request(TITLE, description, CATEGORY), "description", "Mô tả tối đa 255 ký tự");
    }

    @Test
    @DisplayName("UTCID06 - Boundary: category = 30 characters")
    void UTCID06_category30Characters_hasNoValidationError() {
        String category = atLength(CATEGORY, 30);
        assertEquals(30, category.length());
        assertValid(request(TITLE, DESCRIPTION, category));
    }

    @Test
    @DisplayName("UTCID07 - Boundary: category = 31 characters")
    void UTCID07_category31Characters_hasExactlyOneCategoryError() {
        String category = atLength(CATEGORY, 31);
        assertEquals(31, category.length());
        assertInvalid(request(TITLE, DESCRIPTION, category), "category", "Danh mục tối đa 30 ký tự");
    }

    @Test
    @DisplayName("UTCID08 - Abnormal: title is empty")
    void UTCID08_emptyTitle_hasExactlyOneRequiredTitleError() {
        assertInvalid(request("", DESCRIPTION, CATEGORY), "title", "Tên khóa học là bắt buộc");
    }

    @Test
    @DisplayName("UTCID09 - Abnormal: description is empty")
    void UTCID09_emptyDescription_hasExactlyOneRequiredDescriptionError() {
        assertInvalid(request(TITLE, "", CATEGORY), "description", "Mô tả là bắt buộc");
    }

    @Test
    @DisplayName("UTCID10 - Abnormal: category is empty")
    void UTCID10_emptyCategory_hasExactlyOneRequiredCategoryError() {
        assertInvalid(request(TITLE, DESCRIPTION, ""), "category", "Danh mục là bắt buộc");
    }

    @Test
    @DisplayName("UTCID11 - Abnormal: account lookup is empty, NOT_FOUND and no AuditLog")
    void UTCID11_accountNotFound_throws404WithoutSaving() {
        // Same request/email as the sheet, but the lookup must be empty to exercise its 404 expectation.
        // The sheet's 'Expert exists' mark in this column contradicts the expected exception.
        when(users.findByEmailIgnoreCase(EMAIL)).thenReturn(Optional.empty());

        ApiException error = assertThrows(ApiException.class, () -> service.create(EMAIL, request()));

        assertEquals(HttpStatus.NOT_FOUND, error.status());
        assertEquals("Không tìm thấy tài khoản.", error.getMessage());
        verify(users).findByEmailIgnoreCase(EMAIL);
        verifyNoInteractions(courses, auditLogs, media, entityManager);
    }

    private void assertValid(CourseRequest input) {
        assertTrue(validator.validate(input).isEmpty());
        verifyNoInteractions(users, courses, auditLogs, media, entityManager);
    }

    private void assertInvalid(CourseRequest input, String field, String message) {
        var violations = validator.validate(input);
        assertEquals(1, violations.size());
        var violation = violations.iterator().next();
        assertEquals(field, violation.getPropertyPath().toString());
        assertEquals(message, violation.getMessage());
        verifyNoInteractions(users, courses, auditLogs, media, entityManager);
    }
}

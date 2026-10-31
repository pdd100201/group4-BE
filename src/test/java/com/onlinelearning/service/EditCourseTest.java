package com.onlinelearning.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.onlinelearning.dto.course.CourseRequest;
import com.onlinelearning.entity.*;
import com.onlinelearning.exception.ApiException;
import com.onlinelearning.repository.*;
import jakarta.persistence.EntityManager;
import jakarta.validation.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Stream;

import static com.onlinelearning.support.CreateCourseFixtures.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** UC26: cases exported from this source to the Report5.1 workbook. */
@ExtendWith(MockitoExtension.class)
class EditCourseTest {
    private static final long COURSE_ID = 1L;
    private static final String EDIT_TITLE = "[DEMO-COURSE] Java cập nhật";
    private static final String EDIT_DESCRIPTION = "Updated course description.";
    private static final Set<String> passed = new HashSet<>();
    private static ValidatorFactory factory;
    private static Validator validator;

    @Mock CourseRepository courses;
    @Mock UserRepository users;
    @Mock AuditLogRepository auditLogs;
    @Mock EntityManager entityManager;
    @Mock MediaService media;
    @InjectMocks ExpertCourseService service;

    record SheetCase(String id, String type, CourseStatus status, String email,
                     CourseRequest input, String outcome, String field, String message) {
        @Override public String toString() { return id + " - " + outcome; }
    }

    private static CourseRequest input(String title, String description, String category) {
        return request(title, description, category);
    }

    static Stream<SheetCase> sheetCases() {
        return Stream.of(
            new SheetCase("UTCID01", "N", CourseStatus.DRAFT, EMAIL, input(EDIT_TITLE, EDIT_DESCRIPTION, CATEGORY), "UPDATED", null, null),
            new SheetCase("UTCID02", "N", CourseStatus.REJECTED, EMAIL, input(EDIT_TITLE, EDIT_DESCRIPTION, CATEGORY), "UPDATED", null, null),
            new SheetCase("UTCID03", "B", CourseStatus.DRAFT, EMAIL, input(atLength(EDIT_TITLE,30), EDIT_DESCRIPTION, CATEGORY), "VALID", null, null),
            new SheetCase("UTCID04", "B", CourseStatus.DRAFT, EMAIL, input(atLength(EDIT_TITLE,31), EDIT_DESCRIPTION, CATEGORY), "INVALID", "title", "Tên khóa học tối đa 30 ký tự"),
            new SheetCase("UTCID05", "B", CourseStatus.DRAFT, EMAIL, input(EDIT_TITLE, atLength(EDIT_DESCRIPTION,255), CATEGORY), "VALID", null, null),
            new SheetCase("UTCID06", "B", CourseStatus.DRAFT, EMAIL, input(EDIT_TITLE, atLength(EDIT_DESCRIPTION,257), CATEGORY), "INVALID", "description", "Mô tả tối đa 255 ký tự"),
            new SheetCase("UTCID07", "B", CourseStatus.DRAFT, EMAIL, input(EDIT_TITLE, EDIT_DESCRIPTION, atLength(CATEGORY,30)), "VALID", null, null),
            new SheetCase("UTCID08", "B", CourseStatus.DRAFT, EMAIL, input(EDIT_TITLE, EDIT_DESCRIPTION, atLength(CATEGORY,31)), "INVALID", "category", "Danh mục tối đa 30 ký tự"),
            new SheetCase("UTCID09", "A", CourseStatus.DRAFT, EMAIL, input("", EDIT_DESCRIPTION, CATEGORY), "INVALID", "title", "Tên khóa học là bắt buộc"),
            new SheetCase("UTCID10", "A", CourseStatus.DRAFT, EMAIL, input(EDIT_TITLE, "", CATEGORY), "INVALID", "description", "Mô tả là bắt buộc"),
            new SheetCase("UTCID11", "A", CourseStatus.DRAFT, EMAIL, input(EDIT_TITLE, EDIT_DESCRIPTION, ""), "INVALID", "category", "Danh mục là bắt buộc"),
            new SheetCase("UTCID12", "A", CourseStatus.DRAFT, "other.expert@example.com", input(EDIT_TITLE, EDIT_DESCRIPTION, CATEGORY), "FORBIDDEN", null, "Bạn chỉ được quản lý khóa học của mình."),
            new SheetCase("UTCID13", "A", CourseStatus.PENDING_REVIEW, EMAIL, input(EDIT_TITLE, EDIT_DESCRIPTION, CATEGORY), "CONFLICT", null, "Chỉ được sửa khóa học Nháp, Bị từ chối hoặc Đã xuất bản."),
            new SheetCase("UTCID14", "A", null, EMAIL, input(EDIT_TITLE, EDIT_DESCRIPTION, CATEGORY), "NOT_FOUND", null, "Không tìm thấy khóa học."),
            new SheetCase("UTCID15", "A", CourseStatus.ARCHIVED, EMAIL, input(EDIT_TITLE, EDIT_DESCRIPTION, CATEGORY), "CONFLICT", null, "Chỉ được sửa khóa học Nháp, Bị từ chối hoặc Đã xuất bản.")
        );
    }

    @BeforeAll
    static void setup() {
        passed.clear();
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void exportSheetInputsAndResults() throws Exception {
        factory.close();
        List<Map<String, Object>> rows = sheetCases().map(tc -> {
            Map<String,Object> row = new LinkedHashMap<>();
            row.put("id", tc.id()); row.put("type", tc.type());
            row.put("courseId", COURSE_ID); row.put("status", tc.status());
            row.put("ownerId", EXPERT_ID); row.put("ownerEmail", EMAIL);
            row.put("actorEmail", tc.email()); row.put("input", tc.input());
            row.put("outcome", tc.outcome()); row.put("field", tc.field());
            row.put("message", tc.message()); row.put("passed", passed.contains(tc.id()));
            return row;
        }).toList();
        Files.createDirectories(Path.of("target"));
        new ObjectMapper().writerWithDefaultPrettyPrinter()
            .writeValue(Path.of("target/edit-course-sheet-cases.json").toFile(), rows);
    }

    @ParameterizedTest(name="{0}")
    @MethodSource("sheetCases")
    void editCourseMatchesSheet(SheetCase tc) {
        if (tc.outcome().equals("VALID") || tc.outcome().equals("INVALID")) {
            var violations = validator.validate(tc.input());
            if (tc.outcome().equals("VALID")) {
                assertTrue(violations.isEmpty());
            } else {
                assertEquals(1, violations.size());
                var violation = violations.iterator().next();
                assertEquals(tc.field(), violation.getPropertyPath().toString());
                assertEquals(tc.message(), violation.getMessage());
            }
            verifyNoInteractions(courses, users, auditLogs, entityManager, media);
        } else {
            assertTrue(validator.validate(tc.input()).isEmpty());
            Course course = existing(tc.status());
            when(courses.findForUpdate(COURSE_ID)).thenReturn(
                tc.outcome().equals("NOT_FOUND") ? Optional.empty() : Optional.of(course));
            if (tc.outcome().equals("UPDATED")) {
                assertUpdated(tc, course);
            } else {
                ApiException error = assertThrows(ApiException.class,
                    () -> service.update(COURSE_ID, tc.email(), tc.input()));
                assertEquals(HttpStatus.valueOf(tc.outcome()), error.status());
                assertEquals(tc.message(), error.getMessage());
                assertEquals(TITLE, course.getTitle());
                assertEquals(DESCRIPTION, course.getDescription());
                assertEquals(tc.status(), course.getStatus());
                verifyNoInteractions(auditLogs, media, users, entityManager);
            }
            verify(courses).findForUpdate(COURSE_ID);
            verify(courses, never()).save(any(Course.class));
        }
        passed.add(tc.id());
    }

    private Course existing(CourseStatus status) {
        Course course = new Course(); course.setId(COURSE_ID); course.setExpert(expert());
        course.setTitle(TITLE); course.setDescription(DESCRIPTION); course.setCategory(CATEGORY);
        course.setPrice(PRICE); course.setStatus(status);
        return course;
    }

    private void assertUpdated(SheetCase tc, Course course) {
        MediaAsset image = new MediaAsset(); image.setId(IMAGE_ID);
        image.setPurpose("COURSE_IMAGE"); image.setResourceType("image");
        image.setOwner(course.getExpert()); image.setCourse(course);
        when(media.attachment(IMAGE_ID, tc.email(), COURSE_ID, "COURSE_IMAGE")).thenReturn(image);

        var result = service.update(COURSE_ID, tc.email(), tc.input());

        assertEquals(COURSE_ID, result.id());
        assertEquals(tc.status(), result.status());
        assertEquals(tc.input().title(), result.title());
        assertEquals(tc.input().description(), result.description());
        assertEquals(tc.input().category(), result.category());
        assertEquals(PRICE, result.price()); assertEquals(IMAGE_ID, result.thumbnailMediaId());
        assertEquals(EXPERT_ID, course.getExpert().getId()); assertSame(image, course.getThumbnail());
        ArgumentCaptor<AuditLog> audit = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogs).save(audit.capture());
        assertEquals("COURSE_UPDATED", audit.getValue().getAction());
        assertEquals("COURSE", audit.getValue().getTargetType());
        assertEquals("1", audit.getValue().getTargetId());
        assertSame(course.getExpert(), audit.getValue().getActor());
        assertNotNull(audit.getValue().getOccurredAt());
        verify(media).attachment(IMAGE_ID, tc.email(), COURSE_ID, "COURSE_IMAGE");
        verifyNoInteractions(users, entityManager);
    }
}

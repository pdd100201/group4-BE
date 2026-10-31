package com.onlinelearning.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.onlinelearning.entity.*;
import com.onlinelearning.exception.ApiException;
import com.onlinelearning.repository.*;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** UC27: Archive/Restore; the exported cases are the source of the Report5.1 workbook. */
@ExtendWith(MockitoExtension.class)
class UpdateCourseStatusTest {
    private static final String COURSE_TITLE = "[DEMO-COURSE] Spring Boot đã xuất bản";
    private static final Set<String> passed = new HashSet<>();
    @Mock CourseRepository courses;
    @Mock UserRepository users;
    @Mock AuditLogRepository logs;
    @Mock EntityManager em;

    record StatusCase(String id, String action, String type, String role, String email,
                      Long actorId, boolean accountExists, long courseId, boolean courseExists,
                      CourseStatus initialState, String reason, String expected, String message) {
        @Override public String toString() { return action + " / " + id + " / " + expected; }
    }

    static Stream<StatusCase> sheetCases() {
        List<StatusCase> cases = new ArrayList<>();
        for (String action : List.of("ARCHIVE", "RESTORE")) {
            CourseStatus initial = action.equals("ARCHIVE") ? CourseStatus.PUBLISHED : CourseStatus.ARCHIVED;
            String normalReason = action.equals("ARCHIVE") ? "  Course policy violation.  " : "  Compliance review completed.  ";
            for (int i=1; i<=15; i++) {
                String role = i==2 ? "ADMIN" : i==3 ? "EXPERT" : i==4 ? "STUDENT" : "MANAGER";
                long actorId = switch(role) { case "ADMIN" -> 1L; case "EXPERT" -> 3L; case "STUDENT" -> 4L; default -> 2L; };
                String email = role.toLowerCase(Locale.ROOT) + "@onlinelearning.local";
                boolean accountExists = i!=5;
                if (!accountExists) email="missing.moderator@example.invalid";
                String reason = switch(i) {
                    case 6 -> null; case 7 -> ""; case 8 -> "   ";
                    case 9 -> "X"; case 10 -> "X".repeat(255); case 11 -> "X".repeat(256);
                    default -> normalReason;
                };
                CourseStatus state = switch(i) {
                    case 13 -> CourseStatus.DRAFT;
                    case 14 -> CourseStatus.PENDING_REVIEW;
                    case 15 -> action.equals("ARCHIVE") ? CourseStatus.ARCHIVED : CourseStatus.PUBLISHED;
                    default -> initial;
                };
                String expected = i==3 || i==4 || i==5 ? "FORBIDDEN"
                    : i==6 || i==7 || i==8 || i==11 ? "BAD_REQUEST"
                    : i==12 ? "NOT_FOUND" : i>=13 ? "CONFLICT" : "SUCCESS";
                String message = switch(expected) {
                    case "FORBIDDEN" -> i==5 ? "Tài khoản không hợp lệ."
                        : action.equals("ARCHIVE") ? "Chỉ Admin/Manager được thực hiện thao tác này." : "Chỉ Admin/Manager được khôi phục khóa học.";
                    case "BAD_REQUEST" -> action.equals("ARCHIVE") ? "Lý do phải có từ 1 đến 255 ký tự." : "Lý do khôi phục phải có từ 1 đến 255 ký tự.";
                    case "NOT_FOUND" -> "Không tìm thấy khóa học.";
                    case "CONFLICT" -> action.equals("ARCHIVE") ? "Chỉ khóa học Published mới được ẩn." : "Chỉ khóa học ARCHIVED mới được khôi phục.";
                    default -> null;
                };
                cases.add(new StatusCase("UTCID"+String.format("%02d",i), action,
                    i<=2 ? "N" : i>=9 && i<=11 ? "B" : "A", role, email, actorId, accountExists,
                    i==12 ? 999999L : 4L, i!=12, state, reason, expected, message));
            }
        }
        return cases.stream();
    }

    @BeforeAll static void resetResults() { passed.clear(); }

    @ParameterizedTest(name="{0}")
    @MethodSource("sheetCases")
    void updateStatusMatchesSheet(StatusCase tc) {
        User actor = new User(); actor.setId(tc.actorId()); actor.setEmail(tc.email());
        actor.setEnabled(true); actor.setBlocked(false);
        Role role = new Role(); role.setCode(tc.role()); actor.getRoles().add(role);
        when(users.findByEmailIgnoreCase(tc.email())).thenReturn(tc.accountExists() ? Optional.of(actor) : Optional.empty());
        Course course = new Course(); course.setId(tc.courseId()); course.setTitle(COURSE_TITLE);
        course.setDescription("Existing course content."); course.setCategory("Programming");
        course.setPrice(new BigDecimal("299000")); course.setStatus(tc.initialState());
        User owner = new User(); owner.setId(3L); owner.setEmail("expert@onlinelearning.local"); course.setExpert(owner);
        MediaAsset image = new MediaAsset(); image.setId(1001L); course.setThumbnail(image);
        boolean roleAllowed = tc.accountExists() && (tc.role().equals("MANAGER") || tc.role().equals("ADMIN"));
        boolean validReason = tc.reason()!=null && !tc.reason().isBlank() && tc.reason().length()<=255;
        boolean queriesCourse = roleAllowed && validReason;
        if (queriesCourse) when(courses.findForUpdate(tc.courseId())).thenReturn(tc.courseExists() ? Optional.of(course) : Optional.empty());

        if (tc.expected().equals("SUCCESS")) {
            Course result = invoke(tc);
            assertSame(course, result);
            assertEquals(tc.action().equals("ARCHIVE") ? CourseStatus.ARCHIVED : CourseStatus.PUBLISHED, result.getStatus());
            ArgumentCaptor<AuditLog> audit = ArgumentCaptor.forClass(AuditLog.class);
            verify(logs).save(audit.capture());
            assertEquals(tc.action().equals("ARCHIVE") ? "COURSE_ARCHIVED" : "COURSE_RESTORED", audit.getValue().getAction());
            assertEquals("COURSE", audit.getValue().getTargetType());
            assertEquals(Long.toString(tc.courseId()), audit.getValue().getTargetId());
            assertSame(actor, audit.getValue().getActor());
            assertEquals(tc.reason().trim(), audit.getValue().getMetadata());
            assertNotNull(audit.getValue().getOccurredAt());
        } else {
            ApiException error = assertThrows(ApiException.class, () -> invoke(tc));
            assertEquals(HttpStatus.valueOf(tc.expected()), error.status());
            assertEquals(tc.message(), error.getMessage());
            assertEquals(tc.initialState(), course.getStatus()); verifyNoInteractions(logs);
        }
        assertEquals(COURSE_TITLE, course.getTitle());
        assertEquals("Existing course content.", course.getDescription());
        assertEquals("Programming", course.getCategory());
        assertEquals(new BigDecimal("299000"), course.getPrice());
        assertSame(owner, course.getExpert()); assertSame(image, course.getThumbnail());
        verify(users).findByEmailIgnoreCase(tc.email());
        if (queriesCourse) verify(courses).findForUpdate(tc.courseId()); else verifyNoInteractions(courses);
        verify(courses, never()).save(any(Course.class)); verify(courses, never()).delete(any(Course.class));
        verifyNoInteractions(em);
        passed.add(tc.action()+"/"+tc.id());
    }

    private Course invoke(StatusCase tc) {
        return tc.action().equals("ARCHIVE")
            ? new CourseModerationService(courses, users, logs, em).archive(tc.courseId(), tc.email(), tc.reason())
            : new CourseRestorationService(courses, users, logs).restore(tc.courseId(), tc.email(), tc.reason());
    }

    @AfterAll
    static void exportSheetData() throws Exception {
        List<Map<String,Object>> data = sheetCases().map(tc -> {
            Map<String,Object> row = new LinkedHashMap<>();
            row.put("case",tc); row.put("passed",passed.contains(tc.action()+"/"+tc.id())); return row;
        }).toList();
        Files.createDirectories(Path.of("target"));
        new ObjectMapper().writerWithDefaultPrettyPrinter()
            .writeValue(Path.of("target/update-course-status-sheet-cases.json").toFile(), data);
    }
}

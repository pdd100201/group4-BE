package com.onlinelearning.support;

import com.onlinelearning.dto.course.CourseRequest;
import com.onlinelearning.entity.Role;
import com.onlinelearning.entity.User;

import java.math.BigDecimal;

/** Input shared with the Create Course sheet supplied on 19/10/2026. */
public final class CreateCourseFixtures {
    public static final String EMAIL = "expert@onlinelearning.local";
    public static final long EXPERT_ID = 3L;
    public static final String TITLE = "[DEMO-COURSE] Java cơ bản";
    public static final String DESCRIPTION = "no video or no quiz for course.";
    public static final String CATEGORY = "Programming";
    public static final BigDecimal PRICE = new BigDecimal("299000");
    // The sheet says "image". CourseRequest accepts an uploaded media ID, not a string/file.
    // This is a mock asset ID; it is not assumed to exist in the real database.
    public static final long IMAGE_ID = 1001L;

    private CreateCourseFixtures() {}

    public static User expert() {
        User expert = new User();
        expert.setId(EXPERT_ID);
        expert.setEmail(EMAIL);
        expert.setFullName("Online Learning Expert");
        expert.setEnabled(true);
        expert.setBlocked(false);
        Role role = new Role();
        role.setCode("EXPERT");
        expert.getRoles().add(role);
        return expert;
    }

    public static CourseRequest request() {
        return request(TITLE, DESCRIPTION, CATEGORY);
    }

    public static CourseRequest request(String title, String description, String category) {
        return new CourseRequest(title, description, category, PRICE, IMAGE_ID);
    }

    /** Append X to a real input value to obtain an exact Java String length. */
    public static String atLength(String value, int length) {
        if (length < value.length()) throw new IllegalArgumentException("Length must preserve the source input");
        return value + "X".repeat(length - value.length());
    }
}

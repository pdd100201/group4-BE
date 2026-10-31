package com.onlinelearning.dto.course;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static com.onlinelearning.support.CreateCourseFixtures.*;

import static org.junit.jupiter.api.Assertions.*;

class CourseRequestValidationTest {
    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void setup() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void cleanup() {
        factory.close();
    }

    @ParameterizedTest
    @CsvSource({"title,29", "title,30", "description,254", "description,255", "category,29", "category,30"})
    void acceptsValuesAtAndBelowMaximum(String field, int length) {
        assertTrue(validator.validate(request(field, "a".repeat(length))).isEmpty());
    }

    @ParameterizedTest
    @CsvSource({"title,31,Tên khóa học tối đa 30 ký tự", "description,256,Mô tả tối đa 255 ký tự", "category,31,Danh mục tối đa 30 ký tự"})
    void rejectsValuesAboveMaximum(String field, int length, String message) {
        var violations = validator.validate(request(field, "a".repeat(length)));
        assertEquals(1, violations.size());
        var violation = violations.iterator().next();
        assertEquals(field, violation.getPropertyPath().toString());
        assertEquals(message, violation.getMessage());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "   "})
    void rejectsMissingOrBlankText(String value) {
        for (String field : new String[]{"title", "description", "category"}) {
            assertTrue(validator.validate(request(field, value)).stream()
                    .anyMatch(v -> v.getPropertyPath().toString().equals(field)), field);
        }
    }

    private CourseRequest request(String field, String value) {
        return new CourseRequest(
                field.equals("title") ? value : TITLE,
                field.equals("description") ? value : DESCRIPTION,
                field.equals("category") ? value : CATEGORY,
                PRICE, IMAGE_ID);
    }
}

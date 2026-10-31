package com.onlinelearning.dto.course;

import jakarta.validation.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import java.util.stream.Stream;
import static org.junit.jupiter.api.Assertions.*;

class LessonRequestValidationTest {
    static ValidatorFactory factory;
    static Validator validator;
    @BeforeAll static void setup() { factory = Validation.buildDefaultValidatorFactory(); validator = factory.getValidator(); }
    @AfterAll static void close() { factory.close(); }
    record Case(String name, LessonRequest input, String field) { @Override public String toString(){return name;} }
    static Stream<Case> invalidRequests() {
        return Stream.of(
            new Case("title null",new LessonRequest(null,"Content",1,true),"title"),
            new Case("title empty",new LessonRequest("","Content",1,true),"title"),
            new Case("title blank",new LessonRequest(" \t ","Content",1,true),"title"),
            new Case("title 256 characters",new LessonRequest("T".repeat(256),"Content",1,true),"title"),
            new Case("content null",new LessonRequest("Title",null,1,true),"content"),
            new Case("content empty",new LessonRequest("Title","",1,true),"content"),
            new Case("content blank",new LessonRequest("Title"," \n ",1,true),"content"),
            new Case("content 8001 characters",new LessonRequest("Title","C".repeat(8001),1,true),"content"),
            new Case("position null",new LessonRequest("Title","Content",null,true),"position"),
            new Case("position zero",new LessonRequest("Title","Content",0,true),"position"),
            new Case("position negative",new LessonRequest("Title","Content",-1,true),"position"),
            new Case("active null",new LessonRequest("Title","Content",1,null),"active")
        );
    }
    @ParameterizedTest(name="{0}") @MethodSource("invalidRequests")
    void rejectsInvalidField(Case test) {
        var violations=validator.validate(test.input()); assertFalse(violations.isEmpty());
        assertTrue(violations.stream().allMatch(v -> test.field().equals(v.getPropertyPath().toString())));
    }
    static Stream<LessonRequest> validRequests() {
        return Stream.of(new LessonRequest("T","C",1,true),
            new LessonRequest("T".repeat(255),"C".repeat(8000),Integer.MAX_VALUE,false),
            new LessonRequest("Bài học Java","Nội dung bài học",2,false,null),
            new LessonRequest("Title","Content",1,true,10L));
    }
    @ParameterizedTest @MethodSource("validRequests")
    void acceptsValidBoundariesAndOptionalVideo(LessonRequest request) { assertTrue(validator.validate(request).isEmpty()); }
}

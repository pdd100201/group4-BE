package com.onlinelearning.controller;

import com.onlinelearning.entity.RegistrationStatus;
import com.onlinelearning.service.ExpertCourseStudentsService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.HttpStatus;
import java.util.Map;

@RestController @RequiredArgsConstructor
@RequestMapping("/api/v1/expert/courses")
public class ExpertCourseStudentsController {
    private final ExpertCourseStudentsService students;
    @GetMapping("/{id}/registrations")
    public ExpertCourseStudentsService.Result list(Authentication actor,@PathVariable Long id,
            @RequestParam(defaultValue="") String q,@RequestParam(required=false) RegistrationStatus status,
            @RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size) {
        return students.list(actor.getName(),id,q,status,page,size);
    }
    @GetMapping("/{id}/registrations/{registrationId}")
    public ExpertCourseStudentsService.Detail detail(Authentication actor,@PathVariable Long id,@PathVariable Long registrationId) {
        return students.detail(actor.getName(),id,registrationId);
    }
    @ExceptionHandler(org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String,String> invalidParameter(){return Map.of("message","Tham số truy vấn không hợp lệ.");}
}

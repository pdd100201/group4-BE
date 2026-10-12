package com.onlinelearning.controller;

import com.onlinelearning.dto.registration.*;
import com.onlinelearning.service.StudentRegistrationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.net.URI;

@RestController @RequiredArgsConstructor @RequestMapping("/api/v1/student/registrations")
public class StudentRegistrationController {
    private final StudentRegistrationService registrations;
    @PostMapping public ResponseEntity<StudentRegistrationResponse> register(Authentication actor,@Valid @RequestBody RegistrationRequest request) {
        var result=registrations.register(actor.getName(),request);
        return result.reused()?ResponseEntity.ok(result):ResponseEntity.created(URI.create("/api/v1/student/registrations/"+result.id())).body(result);
    }
    @GetMapping public StudentRegistrationPage list(Authentication actor,
        @RequestParam(required=false) String q,
        @RequestParam(required=false) com.onlinelearning.entity.RegistrationStatus status,
        @RequestParam(required=false) com.onlinelearning.entity.PackageDuration packageDuration,
        @RequestParam(required=false) java.math.BigDecimal minAmount,
        @RequestParam(required=false) java.math.BigDecimal maxAmount,
        @RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size) {
        return StudentRegistrationPage.from(registrations.list(actor.getName(),new StudentRegistrationFilter(q,status,packageDuration,minAmount,maxAmount),page,size));
    }
    @GetMapping("/{id}") public StudentRegistrationResponse get(Authentication actor,@PathVariable Long id) { return registrations.get(actor.getName(),id); }
}

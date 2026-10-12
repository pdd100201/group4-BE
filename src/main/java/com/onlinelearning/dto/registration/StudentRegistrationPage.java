package com.onlinelearning.dto.registration;
import org.springframework.data.domain.Page;
import java.util.List;

/** Stable public contract rather than exposing Spring PageImpl serialization. */
public record StudentRegistrationPage(List<StudentRegistrationResponse> content,long totalElements,int totalPages,int number,int size) {
    public static StudentRegistrationPage from(Page<StudentRegistrationResponse> page) {
        return new StudentRegistrationPage(page.getContent(),page.getTotalElements(),page.getTotalPages(),page.getNumber(),page.getSize());
    }
}

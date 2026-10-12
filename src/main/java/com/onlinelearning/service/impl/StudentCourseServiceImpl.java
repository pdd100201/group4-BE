package com.onlinelearning.service.impl;

import com.onlinelearning.dto.course.StudentCourseResponse;
import com.onlinelearning.exception.ApiException;
import com.onlinelearning.repository.CourseRegistrationRepository;
import com.onlinelearning.repository.UserRepository;
import com.onlinelearning.service.StudentCourseService;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service @RequiredArgsConstructor @Transactional(readOnly = true)
public class StudentCourseServiceImpl implements StudentCourseService {
    private final UserRepository users;
    private final CourseRegistrationRepository registrations;

    public Page<StudentCourseResponse> list(String email, String search, String sort, int page, int size) {
        var student = users.findByEmailIgnoreCase(email).orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Tai khoan khong ton tai."));
        if (!student.isEnabled() || student.isBlocked() || student.getRoles().stream().noneMatch(role -> "STUDENT".equals(role.getCode())))
            throw new ApiException(HttpStatus.FORBIDDEN, "Chi Student dang hoat dong duoc xem khoa hoc cua minh.");
        if (page < 0 || size < 1 || size > 100 || search != null && search.length() > 200)
            throw new ApiException(HttpStatus.BAD_REQUEST, "Tim kiem hoac phan trang khong hop le.");
        Sort order = switch (sort) {
            case "newest" -> Sort.by(Sort.Direction.DESC, "validFrom", "id");
            case "title" -> Sort.by("course.title").and(Sort.by(Sort.Direction.DESC, "id"));
            default -> throw new ApiException(HttpStatus.BAD_REQUEST, "Sap xep khong hop le.");
        };
        String keyword = search == null ? "" : search.trim().toLowerCase(java.util.Locale.ROOT);
        String pattern = keyword.isEmpty() ? "" : "%" + keyword.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
        return registrations.findCurrentCourses(student.getId(), Instant.now(), pattern, PageRequest.of(page, size, order))
                .map(StudentCourseResponse::from);
    }
}

package com.onlinelearning.service;

import com.onlinelearning.exception.ApiException;
import com.onlinelearning.repository.CourseRegistrationRepository;
import com.onlinelearning.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StudentEnrollmentServiceTest {
    @Mock UserRepository users;
    @Mock CourseRegistrationRepository registrations;
    @InjectMocks StudentEnrollmentService service;

    @Test
    void rejectsInvalidCourseBeforeCallingRepositories() {
        ApiException error = assertThrows(ApiException.class,
                () -> service.read(0L, "student@example.com"));

        assertEquals(HttpStatus.BAD_REQUEST, error.status());
        verifyNoInteractions(users, registrations);
    }

    @Test
    void rejectsUnknownStudentBeforeReadingRegistrations() {
        when(users.findByEmailIgnoreCase("student@example.com")).thenReturn(Optional.empty());

        ApiException error = assertThrows(ApiException.class,
                () -> service.read(1L, "student@example.com"));

        assertEquals(HttpStatus.UNAUTHORIZED, error.status());
        verify(users).findByEmailIgnoreCase("student@example.com");
        verifyNoInteractions(registrations);
    }
}

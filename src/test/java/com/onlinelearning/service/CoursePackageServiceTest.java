package com.onlinelearning.service;

import com.onlinelearning.dto.course.CoursePackagesRequest;
import com.onlinelearning.dto.course.CoursePackagesRequest.PackageInput;
import com.onlinelearning.entity.*;
import com.onlinelearning.exception.ApiException;
import com.onlinelearning.repository.*;
import com.onlinelearning.service.impl.CoursePackageServiceImpl;
import jakarta.validation.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import java.math.BigDecimal;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** UC25/26, C13: package validation and owner/state checks. */
@ExtendWith(MockitoExtension.class)
class CoursePackageServiceTest {
    @Mock CourseRepository courses;
    @Mock CoursePackageRepository packages;
    @Mock UserRepository users;
    @Mock AuditLogRepository logs;
    private static ValidatorFactory factory;
    private CoursePackageServiceImpl service;
    private User owner;
    private Course course;

    @BeforeAll static void openValidator() { factory = Validation.buildDefaultValidatorFactory(); }
    @AfterAll static void closeValidator() { factory.close(); }
    @BeforeEach void setup() {
        service = new CoursePackageServiceImpl(courses, packages, users, logs, factory.getValidator());
        owner = new User(); owner.setId(2L); owner.setEmail("expert@example.com"); owner.setEnabled(true);
        Role role = new Role(); role.setCode("EXPERT"); owner.getRoles().add(role);
        course = new Course(); course.setId(1L); course.setExpert(owner);
        when(users.findByEmailIgnoreCase(owner.getEmail())).thenReturn(Optional.of(owner));
    }

    private CoursePackagesRequest input(String price, String discount) {
        return new CoursePackagesRequest(Arrays.stream(PackageDuration.values())
                .map(d -> new PackageInput(d, new BigDecimal(price), new BigDecimal(discount), true)).toList());
    }
    private void lock() { when(courses.findForUpdate(1L)).thenReturn(Optional.of(course)); }

    @Test void updatesThreePackagesWithAudit() {
        lock(); when(packages.findByCourseIdOrderByIdAsc(1L)).thenReturn(List.of());
        service.update(1L, owner.getEmail(), input("100000", "10"));
        ArgumentCaptor<CoursePackage> saved = ArgumentCaptor.forClass(CoursePackage.class);
        verify(packages, times(3)).save(saved.capture());
        assertEquals(3, saved.getAllValues().stream().map(CoursePackage::getDuration).distinct().count());
        assertTrue(saved.getAllValues().stream().allMatch(p -> p.getCourse() == course && p.isActive()));
        verify(packages).flush(); verify(logs).save(any(AuditLog.class));
    }

    @ParameterizedTest
    @CsvSource({"-1,0","1.5,0","10000000000,0","1,-0.01","1,100.01","1,0.001"})
    void rejectsInvalidPriceAndDiscountBeforeSaving(String price, String discount) {
        lock();
        assertEquals(HttpStatus.BAD_REQUEST, assertThrows(ApiException.class,
                () -> service.update(1L, owner.getEmail(), input(price, discount))).status());
        verifyNoInteractions(packages, logs);
    }

    @ParameterizedTest
    @CsvSource({"0,0","9999999999,100","1,99.99"})
    void acceptsBoundaryPricesAndDiscounts(String price, String discount) {
        lock(); when(packages.findByCourseIdOrderByIdAsc(1L)).thenReturn(List.of());
        assertDoesNotThrow(() -> service.update(1L, owner.getEmail(), input(price, discount)));
        verify(packages, times(3)).save(any(CoursePackage.class));
    }

    @Test void rejectsDuplicateDuration() {
        lock(); when(packages.findByCourseIdOrderByIdAsc(1L)).thenReturn(List.of());
        PackageInput p = input("1", "0").packages().getFirst();
        assertEquals(HttpStatus.BAD_REQUEST, assertThrows(ApiException.class,
                () -> service.update(1L, owner.getEmail(), new CoursePackagesRequest(List.of(p,p,p)))).status());
        // Transaction rollback is verified by integration tests, not a mocked service call.
        verify(packages, never()).flush(); verifyNoInteractions(logs);
    }

    @ParameterizedTest
    @ValueSource(ints={0,1,2,4})
    void requiresExactlyThreePackages(int count) {
        lock(); PackageInput p = input("1","0").packages().getFirst();
        assertEquals(HttpStatus.BAD_REQUEST, assertThrows(ApiException.class,
                () -> service.update(1L, owner.getEmail(), new CoursePackagesRequest(Collections.nCopies(count, p)))).status());
        verifyNoInteractions(packages, logs);
    }

    @ParameterizedTest
    @EnumSource(value=CourseStatus.class, names={"PENDING_REVIEW","APPROVED","ARCHIVED"})
    void rejectsLockedCourse(CourseStatus status) {
        course.setStatus(status); lock();
        assertEquals(HttpStatus.CONFLICT, assertThrows(ApiException.class,
                () -> service.update(1L, owner.getEmail(), input("1","0"))).status());
        verifyNoInteractions(packages, logs);
    }

    @ParameterizedTest
    @ValueSource(strings={"disabled","blocked","wrongRole"})
    void rejectsUnavailableOrNonExpertAccount(String invalid) {
        switch (invalid) {
            case "disabled" -> owner.setEnabled(false);
            case "blocked" -> owner.setBlocked(true);
            case "wrongRole" -> owner.getRoles().clear();
        }
        assertEquals(HttpStatus.FORBIDDEN, assertThrows(ApiException.class,
                () -> service.update(1L, owner.getEmail(), input("1","0"))).status());
        verifyNoInteractions(courses, packages, logs);
    }

    @Test void rejectsWrongOwner() {
        User other = new User(); other.setId(3L); course.setExpert(other); lock();
        assertEquals(HttpStatus.FORBIDDEN, assertThrows(ApiException.class,
                () -> service.update(1L, owner.getEmail(), input("1","0"))).status());
        verifyNoInteractions(packages, logs);
    }
}

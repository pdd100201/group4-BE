package com.onlinelearning.service;

import com.onlinelearning.entity.*;
import com.onlinelearning.exception.ApiException;
import com.onlinelearning.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;

@Service @RequiredArgsConstructor @Transactional(readOnly=true)
public class StudentEnrollmentService {
    private final UserRepository users;
    private final CourseRegistrationRepository registrations;
    public record Enrollment(boolean hasAccess, Long registrationId, String packageName,
        RegistrationStatus status, Instant validUntil, boolean lifetime) {}
    public Enrollment read(Long courseId, String email) {
        if (courseId == null || courseId <= 0) throw new ApiException(HttpStatus.BAD_REQUEST,"Mã khóa học không hợp lệ.");
        User student=users.findByEmailIgnoreCase(email).orElseThrow(()->new ApiException(HttpStatus.UNAUTHORIZED,"Không tìm thấy tài khoản."));
        if (!student.isEnabled() || student.isBlocked() || student.getRoles().stream().noneMatch(r->"STUDENT".equals(r.getCode())))
            throw new ApiException(HttpStatus.FORBIDDEN,"Chỉ Student đang hoạt động được xem đăng ký của mình.");
        Instant now=Instant.now();
        Specification<CourseRegistration> owned=(root,query,cb)->cb.and(cb.equal(root.get("student").get("id"),student.getId()),cb.equal(root.get("course").get("id"),courseId));
        Specification<CourseRegistration> active=(root,query,cb)->cb.and(cb.equal(root.get("status"),RegistrationStatus.ACTIVE),
            cb.lessThanOrEqualTo(root.get("validFrom"),now),cb.or(cb.isTrue(root.get("lifetimeSnapshot")),cb.greaterThan(root.get("validUntil"),now)));
        var order=PageRequest.of(0,1,Sort.by(Sort.Direction.DESC,"id"));
        var current=registrations.findAll(owned.and(active),order).stream().findFirst();
        if(current.isPresent()) return from(current.get(),true);
        Specification<CourseRegistration> pending=(root,query,cb)->cb.equal(root.get("status"),RegistrationStatus.PENDING_PAYMENT);
        return registrations.findAll(owned.and(pending),order).stream().findFirst().map(r->from(r,false))
            .orElseGet(()->new Enrollment(false,null,null,null,null,false));
    }
    private Enrollment from(CourseRegistration r,boolean access) {
        return new Enrollment(access,r.getId(),r.getPackageNameSnapshot(),r.getStatus(),r.getValidUntil(),Boolean.TRUE.equals(r.getLifetimeSnapshot()));
    }
}

package com.onlinelearning.service.impl;

import com.onlinelearning.dto.course.*;
import com.onlinelearning.entity.*;
import com.onlinelearning.exception.ApiException;
import com.onlinelearning.repository.*;
import com.onlinelearning.service.CoursePackageService;
import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.util.*;

@Service @RequiredArgsConstructor @Transactional
public class CoursePackageServiceImpl implements CoursePackageService {
    private final CourseRepository courses;
    private final CoursePackageRepository packages;
    private final UserRepository users;
    private final AuditLogRepository logs;
    private final Validator validator;

    @Transactional(readOnly=true)
    public List<CoursePackageResponse> get(Long id, String email) {
        owned(id, email, false);
        return packages.findByCourseIdOrderByIdAsc(id).stream().map(CoursePackageResponse::from).toList();
    }

    public List<CoursePackageResponse> update(Long id, String email, CoursePackagesRequest request) {
        Course course = owned(id, email, true);
        if (course.getStatus()!=CourseStatus.DRAFT && course.getStatus()!=CourseStatus.REJECTED && course.getStatus()!=CourseStatus.PUBLISHED)
            throw new ApiException(HttpStatus.CONFLICT,"Khóa học hiện không cho phép sửa gói giá.");
        if (request == null || !validator.validate(request).isEmpty())
            throw new ApiException(HttpStatus.BAD_REQUEST,"Nhập đủ ba gói, giá VNĐ nguyên không âm và giảm giá từ 0 đến 100%.");
        Set<PackageDuration> seen = EnumSet.noneOf(PackageDuration.class);
        Map<PackageDuration,CoursePackage> existing = new EnumMap<>(PackageDuration.class);
        packages.findByCourseIdOrderByIdAsc(id).forEach(p -> existing.put(p.getDuration(),p));
        for (var input: request.packages()) {
            if (!seen.add(input.duration())) throw new ApiException(HttpStatus.BAD_REQUEST,"Không được nhập trùng thời hạn gói.");
            CoursePackage p = existing.getOrDefault(input.duration(),new CoursePackage());
            p.setCourse(course); p.setDuration(input.duration()); p.setPrice(input.price());
            p.setDiscountPercent(input.discountPercent()); p.setActive(input.active()); packages.save(p);
        }
        AuditLog log=new AuditLog();log.setActor(course.getExpert());log.setAction("COURSE_PACKAGES_UPDATED");
        log.setTargetType("COURSE");log.setTargetId(id.toString());log.setOccurredAt(Instant.now());logs.save(log);
        packages.flush();
        return packages.findByCourseIdOrderByIdAsc(id).stream().map(CoursePackageResponse::from).toList();
    }

    private Course owned(Long id,String email,boolean lock) {
        User actor=users.findByEmailIgnoreCase(email).orElseThrow(()->new ApiException(HttpStatus.UNAUTHORIZED,"Tài khoản không tồn tại."));
        if (!actor.isEnabled() || actor.isBlocked() || actor.getRoles().stream().noneMatch(r->"EXPERT".equals(r.getCode())))
            throw new ApiException(HttpStatus.FORBIDDEN,"Chỉ Expert được quản lý gói khóa học.");
        Course course=(lock?courses.findForUpdate(id):courses.findById(id)).orElseThrow(()->new ApiException(HttpStatus.NOT_FOUND,"Không tìm thấy khóa học."));
        if (!course.getExpert().getId().equals(actor.getId())) throw new ApiException(HttpStatus.FORBIDDEN,"Khóa học không thuộc bạn.");
        return course;
    }
}

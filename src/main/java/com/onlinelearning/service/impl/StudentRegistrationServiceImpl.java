package com.onlinelearning.service.impl;

import com.onlinelearning.dto.course.CoursePackageResponse;
import com.onlinelearning.dto.registration.*;
import com.onlinelearning.entity.*;
import com.onlinelearning.exception.ApiException;
import com.onlinelearning.repository.*;
import com.onlinelearning.service.StudentRegistrationService;
import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;

@Service @RequiredArgsConstructor @Transactional
public class StudentRegistrationServiceImpl implements StudentRegistrationService {
    private final CourseRepository courses;
    private final CoursePackageRepository packages;
    private final CourseRegistrationRepository registrations;
    private final UserRepository users;
    private final Validator validator;

    public StudentRegistrationResponse register(String email,RegistrationRequest request) {
        User student=student(email);
        if(request==null || !validator.validate(request).isEmpty()) throw new ApiException(HttpStatus.BAD_REQUEST,"Thông tin đăng ký không hợp lệ.");
        // Course lock serializes duplicate submits and Expert pricing/moderation writes.
        Course course=courses.findForUpdate(request.courseId()).orElseThrow(()->new ApiException(HttpStatus.NOT_FOUND,"Không tìm thấy khóa học."));
        if(course.getStatus()!=CourseStatus.PUBLISHED) throw new ApiException(HttpStatus.CONFLICT,"Khóa học chưa được xuất bản hoặc đã được ẩn.");
        if(registrations.countCurrentAccess(student.getId(),course.getId(),Instant.now())>0)
            throw new ApiException(HttpStatus.CONFLICT,"Bạn đang có quyền học khóa học này.");
        var pending=registrations.findFirstByStudentIdAndCourseIdAndStatusOrderByIdDesc(student.getId(),course.getId(),RegistrationStatus.PENDING_PAYMENT);
        if(pending.isPresent()) return StudentRegistrationResponse.from(pending.get(),true);
        CoursePackage p=packages.findById(request.packageId()).orElseThrow(()->new ApiException(HttpStatus.NOT_FOUND,"Không tìm thấy gói học."));
        if(!p.getCourse().getId().equals(course.getId()) || !p.isActive()) throw new ApiException(HttpStatus.BAD_REQUEST,"Gói học không thuộc khóa học hoặc không còn khả dụng.");
        var price=CoursePackageResponse.from(p);
        CourseRegistration r=new CourseRegistration();r.setStudent(student);r.setCourse(course);r.setCoursePackage(p);
        r.setPackageNameSnapshot(price.name());r.setPackageDurationSnapshot(p.getDuration());r.setValidityMonthsSnapshot(price.durationMonths());
        r.setLifetimeSnapshot(price.lifetime());r.setPriceSnapshot(price.price());r.setDiscountPercentSnapshot(price.discountPercent());
        r.setDiscountSnapshot(price.discountAmount());r.setAmount(price.finalPrice());r.setStudentNameSnapshot(request.fullName().trim());
        r.setStudentEmailSnapshot(student.getEmail());r.setStudentPhoneSnapshot(request.phone().trim());
        r.setStatus(RegistrationStatus.PENDING_PAYMENT);
        // Payment owns activation. No Payment/Transaction, validity or learning access here.
        registrations.saveAndFlush(r);
        return StudentRegistrationResponse.from(r,false);
    }

    @Transactional(readOnly=true)
    public StudentRegistrationResponse get(String email,Long id) {
        User student=student(email);
        var r=registrations.findByIdAndStudentId(id,student.getId()).orElseThrow(()->new ApiException(HttpStatus.NOT_FOUND,"Không tìm thấy đăng ký của bạn."));
        return StudentRegistrationResponse.from(r,false);
    }

    @Transactional(readOnly=true)
    public Page<StudentRegistrationResponse> list(String email,int page,int size) {
        User student=student(email);
        if(page<0 || size<1 || size>100) throw new ApiException(HttpStatus.BAD_REQUEST,"Phân trang không hợp lệ.");
        return registrations.findAll((root,query,cb)->cb.equal(root.get("student").get("id"),student.getId()),
            PageRequest.of(page,size,Sort.by(Sort.Direction.DESC,"id"))).map(r->StudentRegistrationResponse.from(r,false));
    }

    private User student(String email) {
        User u=users.findByEmailIgnoreCase(email).orElseThrow(()->new ApiException(HttpStatus.UNAUTHORIZED,"Tài khoản không tồn tại."));
        if(!u.isEnabled() || u.isBlocked() || u.getRoles().stream().noneMatch(r->"STUDENT".equals(r.getCode())))
            throw new ApiException(HttpStatus.FORBIDDEN,"Chỉ Student đang hoạt động được đăng ký khóa học.");
        return u;
    }
}

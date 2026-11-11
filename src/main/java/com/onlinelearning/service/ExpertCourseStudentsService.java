package com.onlinelearning.service;

import com.onlinelearning.entity.*;
import com.onlinelearning.dto.registration.StudentRegistrationResponse;
import com.onlinelearning.exception.ApiException;
import com.onlinelearning.repository.*;
import jakarta.persistence.EntityManager;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Service @RequiredArgsConstructor @Transactional(readOnly=true)
public class ExpertCourseStudentsService {
    private final CourseRepository courses;
    private final UserRepository users;
    private final CourseRegistrationRepository registrations;
    private final EntityManager em;

    public record StudentRow(Long registrationId,Long studentId,String studentName,String studentEmail,
            String packageName,BigDecimal amount,RegistrationStatus status,PaymentStatus paymentStatus,
            Instant createdAt,Instant validFrom,Instant validUntil,boolean lifetime,String accessStatus) {}
    public record Summary(long students,long registrations,long currentStudents,long expiredRegistrations) {}
    public record Result(Long courseId,String courseTitle,CourseStatus courseStatus,Summary summary,
            List<StudentRow> items,int page,int size,long totalElements,int totalPages) {}

    public Result list(String email,Long courseId,String q,RegistrationStatus status,int page,int size) {
        if(courseId==null||courseId<=0||page<0||size<1||size>100||q!=null&&q.length()>200
                ||status!=null&&status!=RegistrationStatus.ACTIVE&&status!=RegistrationStatus.EXPIRED)
            throw new ApiException(HttpStatus.BAD_REQUEST,"Bộ lọc hoặc phân trang không hợp lệ.");
        var course=ownedCourse(email,courseId);
        String search=q==null?"":q.trim().toLowerCase(Locale.ROOT);
        String pattern="%"+search.replace("\\","\\\\").replace("%","\\%").replace("_","\\_")+"%";
        Instant now=Instant.now();
        var result=registrations.findAll((root,query,cb)->{
            List<Predicate> filters=new ArrayList<>();
            filters.add(cb.equal(root.get("course").get("id"),courseId));
            filters.add(root.get("status").in(RegistrationStatus.ACTIVE,RegistrationStatus.EXPIRED));
            if(status==RegistrationStatus.ACTIVE) filters.add(cb.and(
                cb.equal(root.get("status"),RegistrationStatus.ACTIVE),
                cb.lessThanOrEqualTo(root.get("validFrom"),now),
                cb.or(cb.isTrue(root.get("lifetimeSnapshot")),cb.greaterThan(root.get("validUntil"),now))));
            if(status==RegistrationStatus.EXPIRED) filters.add(cb.or(
                cb.equal(root.get("status"),RegistrationStatus.EXPIRED),
                cb.and(cb.equal(root.get("status"),RegistrationStatus.ACTIVE),
                    cb.lessThanOrEqualTo(root.get("validFrom"),now),
                    cb.or(cb.isNull(root.get("lifetimeSnapshot")),cb.isFalse(root.get("lifetimeSnapshot"))),
                    cb.or(cb.isNull(root.get("validUntil")),cb.lessThanOrEqualTo(root.get("validUntil"),now)))));
            if(!search.isEmpty()) {
                var byName=cb.like(cb.lower(root.get("student").get("fullName")),pattern,'\\');
                var byEmail=cb.like(cb.lower(root.get("student").get("email")),pattern,'\\');
                Predicate match=cb.or(byName,byEmail);
                if(search.matches("#?[1-9][0-9]{0,17}")) match=cb.or(match,cb.equal(root.get("id"),Long.parseLong(search.replace("#",""))));
                filters.add(match);
            }
            return cb.and(filters.toArray(Predicate[]::new));
        },PageRequest.of(page,size,Sort.by(Sort.Direction.DESC,"id")));
        var ids=result.getContent().stream().map(CourseRegistration::getId).toList();
        Map<Long,PaymentStatus> paid=ids.isEmpty()?Map.of():em.createQuery("select p from Payment p where p.registration.id in :ids",Payment.class)
            .setParameter("ids",ids).getResultList().stream().collect(Collectors.toMap(p->p.getRegistration().getId(),Payment::getStatus));
        var rows=result.map(r->new StudentRow(r.getId(),r.getStudent().getId(),r.getStudent().getFullName(),r.getStudent().getEmail(),
            r.getPackageNameSnapshot(),r.getAmount(),r.getStatus(),paid.get(r.getId()),r.getCreatedAt(),r.getValidFrom(),r.getValidUntil(),
            Boolean.TRUE.equals(r.getLifetimeSnapshot()),access(r,now))).getContent();
        Object[] counts=em.createQuery("select count(r),count(distinct r.student.id) from CourseRegistration r where r.course.id=:id and r.status in (com.onlinelearning.entity.RegistrationStatus.ACTIVE,com.onlinelearning.entity.RegistrationStatus.EXPIRED)",Object[].class)
            .setParameter("id",courseId).getSingleResult();
        long current=em.createQuery("select count(distinct r.student.id) from CourseRegistration r where r.course.id=:id and r.status=com.onlinelearning.entity.RegistrationStatus.ACTIVE and r.validFrom<=:now and (r.lifetimeSnapshot=true or r.validUntil>:now)",Long.class)
            .setParameter("id",courseId).setParameter("now",now).getSingleResult();
        long expired=em.createQuery("select count(r) from CourseRegistration r where r.course.id=:id and (r.status=com.onlinelearning.entity.RegistrationStatus.EXPIRED or (r.status=com.onlinelearning.entity.RegistrationStatus.ACTIVE and r.validFrom<=:now and (r.lifetimeSnapshot is null or r.lifetimeSnapshot=false) and (r.validUntil is null or r.validUntil<=:now)))",Long.class)
            .setParameter("id",courseId).setParameter("now",now).getSingleResult();
        var summary=new Summary(((Number)counts[1]).longValue(),((Number)counts[0]).longValue(),current,expired);
        return new Result(courseId,course.getTitle(),course.getStatus(),summary,rows,result.getNumber(),result.getSize(),result.getTotalElements(),result.getTotalPages());
    }

    public record Detail(StudentRegistrationResponse registration,Long studentId,PaymentStatus paymentStatus,String provider,String accessStatus) {}

    public Detail detail(String email,Long courseId,Long registrationId) {
        if(courseId==null||courseId<=0||registrationId==null||registrationId<=0)
            throw new ApiException(HttpStatus.BAD_REQUEST,"Mã khóa học hoặc đăng ký không hợp lệ.");
        ownedCourse(email,courseId);
        var r=registrations.findById(registrationId)
            .filter(row->row.getCourse().getId().equals(courseId))
            .filter(row->row.getStatus()==RegistrationStatus.ACTIVE||row.getStatus()==RegistrationStatus.EXPIRED)
            .orElseThrow(()->new ApiException(HttpStatus.NOT_FOUND,"Đăng ký không còn trong danh sách học viên của khóa học này."));
        var payment=em.createQuery("select p from Payment p where p.registration.id=:id",Payment.class)
            .setParameter("id",registrationId).getResultList().stream().findFirst().orElse(null);
        return new Detail(StudentRegistrationResponse.from(r,false),r.getStudent().getId(),payment==null?null:payment.getStatus(),
            payment==null?null:payment.getProvider(),access(r,Instant.now()));
    }

    private Course ownedCourse(String email,Long courseId) {
        var actor=users.findByEmailIgnoreCase(email).orElseThrow(()->new ApiException(HttpStatus.UNAUTHORIZED,"Tài khoản không tồn tại."));
        if(!actor.isEnabled()||actor.isBlocked()||actor.getRoles().stream().noneMatch(r->"EXPERT".equals(r.getCode())))
            throw new ApiException(HttpStatus.FORBIDDEN,"Chỉ Expert đang hoạt động được xem danh sách học viên.");
        var course=courses.findById(courseId).orElseThrow(()->new ApiException(HttpStatus.NOT_FOUND,"Không tìm thấy khóa học."));
        if(!course.getExpert().getId().equals(actor.getId()))
            throw new ApiException(HttpStatus.FORBIDDEN,"Bạn chỉ được xem học viên đăng ký khóa học của mình.");
        return course;
    }

    private String access(CourseRegistration r,Instant now) {
        if(r.getStatus()==RegistrationStatus.EXPIRED) return "EXPIRED";
        if(r.getStatus()!=RegistrationStatus.ACTIVE||r.getValidFrom()==null) return "NOT_ACTIVATED";
        if(r.getValidFrom().isAfter(now)) return "UPCOMING";
        if(Boolean.TRUE.equals(r.getLifetimeSnapshot())||r.getValidUntil()!=null&&r.getValidUntil().isAfter(now)) return "CURRENT";
        return "EXPIRED";
    }
}

package com.onlinelearning.service;

import com.onlinelearning.entity.*;
import com.onlinelearning.exception.ApiException;
import com.onlinelearning.repository.*;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.util.Objects;

@Service @RequiredArgsConstructor @Transactional
public class CourseDeletionService {
    private final CourseRepository courses;
    private final UserRepository users;
    private final AuditLogRepository logs;
    private final NotificationService notifications;
    private final EntityManager em;

    public record RequestView(Long id, String title, String description, String category,
        String expertName, String expertEmail, String courseStatus, String requestStatus,
        String reason, Instant requestedAt, String reviewReason, Instant reviewedAt,
        String reviewedBy, long registrations, long lessons, long questions) {}

    public static void requireMutable(Course c) {
        if (c.getDeletedAt() != null) throw new ApiException(HttpStatus.NOT_FOUND,"Khóa học đã bị xóa.");
        if (c.isDeletionPending()) throw new ApiException(HttpStatus.CONFLICT,"Khóa học đang chờ Manager duyệt xóa, chưa thể chỉnh sửa.");
    }
    private User actor(String email, String... roles) {
        User u=users.findByEmailIgnoreCase(email).orElseThrow(()->new ApiException(HttpStatus.UNAUTHORIZED,"Tài khoản không hợp lệ."));
        if(!u.isEnabled() || u.isBlocked() || u.getRoles().stream().noneMatch(r->java.util.Arrays.asList(roles).contains(r.getCode())))
            throw new ApiException(HttpStatus.FORBIDDEN,"Bạn không có quyền thực hiện thao tác này.");
        return u;
    }
    private Course locked(Long id) {
        return courses.findForUpdate(id).orElseThrow(()->new ApiException(HttpStatus.NOT_FOUND,"Không tìm thấy khóa học."));
    }
    private Course owned(Long id, User u) {
        Course c=locked(id);
        if(!c.getExpert().getId().equals(u.getId())) throw new ApiException(HttpStatus.FORBIDDEN,"Bạn chỉ được quản lý khóa học của mình.");
        requireMutable(c); return c;
    }
    private String reason(String text) {
        if(text==null || text.isBlank() || text.trim().length()>1000)
            throw new ApiException(HttpStatus.BAD_REQUEST,"Nhập lý do từ 1 đến 1.000 ký tự.");
        return text.trim();
    }
    public void deleteDraft(Long id,String email) {
        User u=actor(email,"EXPERT"); Course c=owned(id,u);
        if(c.requiresDeletionApproval()) throw new ApiException(HttpStatus.CONFLICT,"Khóa học đã từng xuất bản. Hãy gửi yêu cầu xóa kèm lý do cho Manager.");
        remove(c); audit(c,u,"COURSE_DELETED","Expert xóa khóa học chưa từng xuất bản.");
    }
    public void request(Long id,String email,String text) {
        User u=actor(email,"EXPERT"); Course c=owned(id,u);
        if(!c.requiresDeletionApproval()) throw new ApiException(HttpStatus.CONFLICT,"Khóa học chưa xuất bản có thể xóa trực tiếp.");
        c.setDeletionRequestReason(reason(text)); c.setDeletionRequestStatus("PENDING");
        c.setDeletionRequestedAt(Instant.now()); c.setDeletionReviewReason(null);
        c.setDeletionReviewedAt(null); c.setDeletionReviewedBy(null);
        String key=audit(c,u,"COURSE_DELETION_REQUESTED",c.getDeletionRequestReason());
        users.search(null,"MANAGER",true,false,Pageable.unpaged()).forEach(reviewer ->
            notifications.send(reviewer.getId(),key,"COURSE_DELETION_REQUESTED","REVIEW","Yêu cầu xóa khóa học",
                u.getFullName()+" yêu cầu xóa khóa học “"+c.getTitle()+"”. Lý do: "+c.getDeletionRequestReason(),"/manager/course-deletion-requests"));
    }
    @Transactional(readOnly=true)
    public Page<RequestView> list(String email,String status,int page,int size) {
        actor(email,"MANAGER");
        if(status!=null && !status.isBlank() && !java.util.Set.of("PENDING","APPROVED","REJECTED").contains(status))
            throw new ApiException(HttpStatus.BAD_REQUEST,"Trạng thái không hợp lệ.");
        Specification<Course> spec=(r,q,cb)->cb.isNotNull(r.get("deletionRequestedAt"));
        if(status!=null && !status.isBlank()) spec=spec.and((r,q,cb)->cb.equal(r.get("deletionRequestStatus"),status));
        return courses.findAll(spec,PageRequest.of(Math.max(0,page),Math.max(1,Math.min(50,size)),Sort.by(Sort.Direction.DESC,"deletionRequestedAt").and(Sort.by(Sort.Direction.DESC,"id")))).map(this::view);
    }
    public RequestView review(Long id,String email,boolean approve,String text,Instant expected) {
        User u=actor(email,"MANAGER"); Course c=locked(id);
        if(!c.isDeletionPending() || c.getDeletedAt()!=null || expected==null || c.getDeletionRequestedAt()==null || !Objects.equals(c.getDeletionRequestedAt(),expected))
            throw new ApiException(HttpStatus.CONFLICT,"Yêu cầu đã thay đổi hoặc đã được xử lý. Vui lòng tải lại danh sách.");
        String note=approve ? (text==null || text.isBlank()?"Đồng ý xóa khóa học.":reason(text)) : reason(text);
        c.setDeletionRequestStatus(approve?"APPROVED":"REJECTED"); c.setDeletionReviewReason(note);
        c.setDeletionReviewedAt(Instant.now()); c.setDeletionReviewedBy(u);
        if(approve) remove(c);
        String key=audit(c,u,approve?"COURSE_DELETION_APPROVED":"COURSE_DELETION_REJECTED",note+" | Yêu cầu của Expert: "+c.getDeletionRequestReason());
        notifications.send(c.getExpert().getId(),key,"COURSE_DELETION_REVIEWED","REVIEW",approve?"Đã duyệt xóa khóa học":"Yêu cầu xóa bị từ chối",
            "Khóa học “"+c.getTitle()+"”: "+note,"/expert/courses");
        return view(c);
    }
    private void remove(Course c) { c.setDeletedAt(Instant.now()); c.setStatus(CourseStatus.ARCHIVED); }
    private String audit(Course c,User u,String action,String reason) {
        AuditLog log=new AuditLog(); log.setActor(u);log.setAction(action);log.setOccurredAt(Instant.now());
        log.setTargetType("COURSE");log.setTargetId(c.getId().toString());log.setMetadata(reason);logs.saveAndFlush(log);
        return "course-deletion:"+log.getId();
    }
    private long count(String entity,Long id) { return em.createQuery("select count(e) from "+entity+" e where e.course.id=:id",Long.class).setParameter("id",id).getSingleResult(); }
    private RequestView view(Course c) {
        return new RequestView(c.getId(),c.getTitle(),c.getDescription(),c.getCategory(),c.getExpert().getFullName(),c.getExpert().getEmail(),
            c.getStatus().name(),c.getDeletionRequestStatus(),c.getDeletionRequestReason(),c.getDeletionRequestedAt(),c.getDeletionReviewReason(),
            c.getDeletionReviewedAt(),c.getDeletionReviewedBy()==null?null:c.getDeletionReviewedBy().getFullName(),
            count("CourseRegistration",c.getId()),count("Lesson",c.getId()),count("Question",c.getId()));
    }
}

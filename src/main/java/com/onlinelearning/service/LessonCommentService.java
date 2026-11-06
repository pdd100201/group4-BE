package com.onlinelearning.service;

import com.onlinelearning.entity.*;
import com.onlinelearning.repository.*;
import com.onlinelearning.exception.ApiException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Service @RequiredArgsConstructor @Transactional(readOnly=true)
public class LessonCommentService {
    private final UserRepository users;
    private final LessonRepository lessons;
    private final CourseRegistrationRepository registrations;
    private final LessonCommentRepository comments;
    private final LessonCommentLikeRepository likes;

    public record CommentView(Long id,Long lessonId,Long parentId,Long authorId,String authorName,
        String avatarUrl,String content,Instant createdAt,long likeCount,boolean likedByMe,long replyCount,boolean edited,boolean deleted) {}

    public Page<CommentView> list(String email,Long lessonId,Long parentId,int page) {
        User actor=access(email,lessonId);
        if (parentId!=null) root(lessonId,parentId);
        if (page<0 || page>100000) throw bad("Trang bình luận không hợp lệ.");
        var paging=PageRequest.of(page,20,Sort.by(parentId==null?Sort.Direction.DESC:Sort.Direction.ASC,"id"));
        var result=parentId==null?comments.findByLessonIdAndParentIsNullAndDeletedFalse(lessonId,paging)
            :comments.findByLessonIdAndParentIdAndDeletedFalse(lessonId,parentId,paging);
        var ids=result.stream().map(LessonComment::getId).toList();
        if (ids.isEmpty()) return result.map(c->view(c,0,false,0));
        var counts=counts(likes.likeCounts(ids));
        var replies=counts(comments.replyCounts(ids));
        var liked=likes.likedIds(ids,actor.getId());
        return result.map(c->view(c,counts.getOrDefault(c.getId(),0L),liked.contains(c.getId()),replies.getOrDefault(c.getId(),0L)));
    }

    @Transactional(isolation=org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public CommentView create(String email,Long lessonId,Long parentId,String content) {
        User actor=access(email,lessonId);
        if (content==null || content.isBlank() || content.strip().length()>2000) throw bad("Bình luận cần từ 1 đến 2.000 ký tự.");
        var comment=new LessonComment();
        comment.setLesson(lessons.getReferenceById(lessonId));comment.setAuthor(actor);
        if (parentId!=null) {
            var parent=comments.findForUpdate(parentId,lessonId).filter(c->c.getParent()==null&&!c.isDeleted()).orElseThrow(this::missing);
            comment.setParent(parent);
        }
        comment.setContent(content.strip());
        comments.saveAndFlush(comment);
        return view(comment,0,false,0);
    }

    @Transactional(isolation=org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public CommentView like(String email,Long lessonId,Long commentId,boolean liked) {
        User actor=access(email,lessonId);
        // Serialize changes for this comment; repeated requests cannot duplicate a student's like.
        var comment=comments.findForUpdate(commentId,lessonId).filter(this::visible).orElseThrow(this::missing);
        var existing=likes.findByCommentIdAndStudentId(commentId,actor.getId());
        if (liked && existing.isEmpty()) {
            var like=new LessonCommentLike();like.setComment(comment);like.setStudent(actor);likes.save(like);
        } else if (!liked) existing.ifPresent(likes::delete);
        likes.flush();
        var ids=List.of(commentId);
        return view(comment,counts(likes.likeCounts(ids)).getOrDefault(commentId,0L),liked,
            counts(comments.replyCounts(ids)).getOrDefault(commentId,0L));
    }

    @Transactional(isolation=org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public CommentView update(String email,Long lessonId,Long commentId,String content) {
        User actor=access(email,lessonId);
        var comment=owned(actor,lessonId,commentId);
        if (!visible(comment)) throw missing();
        if (content==null || content.isBlank() || content.strip().length()>2000) throw bad("Bình luận cần từ 1 đến 2.000 ký tự.");
        if (!comment.getContent().equals(content.strip())) {
            comment.setContent(content.strip());comment.setUpdatedAt(Instant.now());comments.flush();
        }
        var ids=List.of(commentId);
        return view(comment,counts(likes.likeCounts(ids)).getOrDefault(commentId,0L),
            likes.findByCommentIdAndStudentId(commentId,actor.getId()).isPresent(),
            counts(comments.replyCounts(ids)).getOrDefault(commentId,0L));
    }

    @Transactional(isolation=org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public void delete(String email,Long lessonId,Long commentId) {
        var comment=owned(access(email,lessonId),lessonId,commentId);
        // Hide and clear the entire thread atomically; a reply has no children.
        comments.deleteThread(comment.getId());
    }

    private LessonComment owned(User actor,Long lessonId,Long commentId) {
        var comment=comments.findForUpdate(commentId,lessonId).orElseThrow(this::missing);
        if (!comment.getAuthor().getId().equals(actor.getId()))
            throw new ApiException(HttpStatus.FORBIDDEN,"Bạn chỉ có thể sửa hoặc xóa bình luận của mình.");
        return comment;
    }

    private User access(String email,Long lessonId) {
        var actor=users.findByEmailIgnoreCase(email).filter(u->u.isEnabled()&&!u.isBlocked()
            &&u.getRoles().stream().anyMatch(r->"STUDENT".equals(r.getCode())))
            .orElseThrow(this::forbidden);
        var lesson=lessons.findById(lessonId).filter(l->l.isActive()&&l.getCourse().getStatus()==CourseStatus.PUBLISHED)
            .orElseThrow(this::missing);
        if (registrations.countCurrentAccess(actor.getId(),lesson.getCourse().getId(),Instant.now())==0) throw forbidden();
        return actor;
    }
    private LessonComment root(Long lessonId,Long id) {
        return comments.findById(id).filter(c->c.getLesson().getId().equals(lessonId)&&c.getParent()==null&&!c.isDeleted())
            .orElseThrow(this::missing);
    }
    private boolean visible(LessonComment comment) {
        return !comment.isDeleted()&&(comment.getParent()==null||!comment.getParent().isDeleted());
    }
    private Map<Long,Long> counts(List<Object[]> rows) {
        return rows.stream().collect(Collectors.toMap(r->(Long)r[0],r->((Number)r[1]).longValue()));
    }
    private CommentView view(LessonComment c,long count,boolean liked,long replies) {
        var author=c.getAuthor();
        return new CommentView(c.getId(),c.getLesson().getId(),c.getParent()==null?null:c.getParent().getId(),
            author.getId(),author.getFullName(),author.getAvatarUrl(),c.isDeleted()?"Bình luận đã được xóa.":c.getContent(),c.getCreatedAt(),
            c.isDeleted()?0:count,!c.isDeleted()&&liked,replies,c.getUpdatedAt()!=null,c.isDeleted());
    }
    private ApiException forbidden(){return new ApiException(HttpStatus.FORBIDDEN,"Chỉ học viên có quyền học còn hiệu lực mới được xem và tương tác bình luận bài học.");}
    private ApiException missing(){return new ApiException(HttpStatus.NOT_FOUND,"Không tìm thấy bài học hoặc bình luận.");}
    private ApiException bad(String message){return new ApiException(HttpStatus.BAD_REQUEST,message);}
}

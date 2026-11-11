package com.onlinelearning.service;

import com.onlinelearning.dto.blog.*;
import com.onlinelearning.entity.*;
import com.onlinelearning.exception.ApiException;
import com.onlinelearning.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;

@Service @RequiredArgsConstructor @Transactional(readOnly=true)
public class CommentService {
    private final PostCommentRepository comments;
    private final LessonCommentLikeRepository likes;
    private final PostRepository posts;
    private final UserRepository users;
    private final PublicPostService publicPosts;
    private final BlogActorService actors;
    private final AuditLogRepository audit;
    private final jakarta.persistence.EntityManager em;

    public BlogPage<CommentResponse> list(Long postId, String email, int page, int size) {
        publicPosts.visible(postId);
        if (page < 0 || size < 1 || size > 100) throw error(HttpStatus.BAD_REQUEST, "Phân trang không hợp lệ.");
        User viewer = viewer(email);
        return pageViews(comments.findByPostIdAndParentIdIsNullAndStatus(postId, CommentStatus.VISIBLE,
            PageRequest.of(page, size, Sort.by(Sort.Direction.ASC, "id"))),viewer);
    }
    public BlogPage<CommentResponse> replies(Long postId,Long parentId,String email,int page,int size) {
        publicPosts.visible(postId);
        if(page<0||size<1||size>100) throw error(HttpStatus.BAD_REQUEST,"Phân trang không hợp lệ.");
        root(postId,parentId);
        return pageViews(comments.findByPostIdAndParentIdAndStatus(postId,parentId,CommentStatus.VISIBLE,
            PageRequest.of(page,size,Sort.by(Sort.Direction.ASC,"id"))),viewer(email));
    }
    @Transactional public CommentResponse reply(Long postId,Long parentId,String email,CommentRequest request) {
        User actor=actors.require(email,"STUDENT");
        Post post=visibleLocked(postId);
        root(postId,parentId);
        var c=new PostComment();c.setPost(post);c.setParentId(parentId);c.setAuthor(actor);c.setContent(content(request));
        comments.saveAndFlush(c);record(actor,c,"CREATE_COMMENT_REPLY");return response(c,actor);
    }
    private PostComment root(Long postId,Long id) {
        return comments.findByIdAndPostIdAndStatus(id,postId,CommentStatus.VISIBLE).filter(c->c.getParentId()==null).orElseThrow(this::missing);
    }
    public CommentResponse detail(Long postId, Long id, String email) {
        publicPosts.visible(postId);
        if (id == null || id <= 0) throw error(HttpStatus.BAD_REQUEST, "Mã bình luận không hợp lệ.");
        PostComment c = comments.findByIdAndPostIdAndStatus(id, postId, CommentStatus.VISIBLE).orElseThrow(() -> missing());
        if(c.getParentId()!=null) root(postId,c.getParentId());
        return response(c, viewer(email));
    }
    public BlogPage<CommentResponse> moderationList(Long postId, String email, CommentStatus status, int page, int size) {
        User actor = actors.require(email, "MANAGER", "ADMIN");
        if (postId == null || postId <= 0 || page < 0 || size < 1 || size > 100)
            throw error(HttpStatus.BAD_REQUEST, "Mã bài viết hoặc phân trang không hợp lệ.");
        posts.findById(postId).filter(p -> !p.isDeleted()).orElseThrow(() -> missing());
        if (status == CommentStatus.DELETED) throw error(HttpStatus.BAD_REQUEST, "Bình luận đã xóa không nằm trong danh sách moderation.");
        return pageViews(comments.findAll((root, query, cb) -> {
            if (query != null && query.getResultType() != Long.class) root.fetch("author");
            return cb.and(cb.equal(root.get("post").get("id"), postId), status == null
                ? cb.notEqual(root.get("status"), CommentStatus.DELETED) : cb.equal(root.get("status"), status));
        }, PageRequest.of(page, size, Sort.by(Sort.Direction.ASC, "id"))),actor);
    }
    public CommentResponse moderationDetail(Long postId, Long id, String email) {
        User actor = actors.require(email, "MANAGER", "ADMIN");
        PostComment c = comments.findById(id).filter(comment -> comment.getPost().getId().equals(postId)
            && !comment.getPost().isDeleted() && comment.getStatus() != CommentStatus.DELETED).orElseThrow(() -> missing());
        return response(c, actor);
    }
    @Transactional public CommentResponse create(Long postId, String email, CommentRequest request) {
        User student = actors.require(email, "STUDENT");
        Post post = visibleLocked(postId);
        PostComment c = new PostComment(); c.setPost(post); c.setAuthor(student); c.setContent(content(request));
        comments.saveAndFlush(c); record(student, c, "CREATE_COMMENT"); return response(c, student);
    }
    @Transactional public CommentResponse update(Long id, String email, CommentRequest request) {
        User student = actors.require(email, "STUDENT");
        PostComment c = locked(id);
        if (!c.getAuthor().getId().equals(student.getId())) throw error(HttpStatus.FORBIDDEN, "Bạn chỉ được sửa bình luận của mình.");
        requireEditable(c); c.setContent(content(request)); c.setUpdatedAt(Instant.now());
        record(student, c, "UPDATE_COMMENT"); return response(c, student);
    }
    @Transactional public void delete(Long id, String email) {
        User actor = actors.require(email, "STUDENT", "MANAGER", "ADMIN");
        PostComment c = locked(id);
        boolean moderator = actor.getRoles().stream().anyMatch(r -> "MANAGER".equals(r.getCode()) || "ADMIN".equals(r.getCode()));
        if (!moderator) {
            if (!c.getAuthor().getId().equals(actor.getId())) throw error(HttpStatus.FORBIDDEN, "Bạn chỉ được xóa bình luận của mình.");
            requireEditable(c);
        }
        if (c.getStatus() == CommentStatus.DELETED) throw error(HttpStatus.CONFLICT, "Bình luận đã được xóa.");
        c.setStatus(CommentStatus.DELETED); c.setUpdatedAt(Instant.now()); record(actor, c, "DELETE_COMMENT");
    }
    @Transactional public CommentResponse status(Long id, String email, CommentStatus next) {
        User actor = actors.require(email, "MANAGER", "ADMIN");
        PostComment c = locked(id);
        if (next == null || next == CommentStatus.DELETED) throw error(HttpStatus.BAD_REQUEST, "Dùng thao tác xóa để xóa bình luận.");
        if (c.getStatus() == CommentStatus.DELETED || c.getPost().isDeleted()) throw error(HttpStatus.CONFLICT, "Không thể khôi phục bình luận hoặc bài viết đã xóa.");
        if (next == c.getStatus()) throw error(HttpStatus.CONFLICT, "Bình luận đã ở trạng thái này.");
        c.setStatus(next); c.setUpdatedAt(Instant.now()); record(actor, c, "UPDATE_COMMENT_STATUS");
        return response(c, actor);
    }
    @Transactional(isolation=org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public CommentResponse like(Long id,String email,boolean liked) {
        User actor=actors.require(email,"STUDENT","EXPERT","MANAGER","ADMIN");
        PostComment c=locked(id);
        requireEditable(c);
        var existing=likes.findByCommentIdAndStudentId(id,actor.getId());
        if(liked && existing.isEmpty()) {
            var like=new LessonCommentLike();like.setComment(c);like.setStudent(actor);likes.save(like);
        } else if(!liked) existing.ifPresent(likes::delete);
        likes.flush();
        return response(c,actor);
    }
    private BlogPage<CommentResponse> pageViews(Page<PostComment> page,User viewer) {
        var ids=page.getContent().stream().map(PostComment::getId).toList();
        var counts=new java.util.HashMap<Long,Long>();
        if(!ids.isEmpty()) for(var row:likes.likeCounts(ids)) counts.put(((Number)row[0]).longValue(),((Number)row[1]).longValue());
        var replies=new java.util.HashMap<Long,Long>();
        if(!ids.isEmpty()) for(var row:comments.replyCounts(ids)) replies.put(((Number)row[0]).longValue(),((Number)row[1]).longValue());
        var liked=viewer==null||ids.isEmpty()?java.util.Set.<Long>of():likes.likedIds(ids,viewer.getId());
        return BlogPage.from(page.map(c->CommentResponse.from(c,viewer,counts.getOrDefault(c.getId(),0L),liked.contains(c.getId()),replies.getOrDefault(c.getId(),0L))));
    }
    private CommentResponse response(PostComment c,User viewer) {
        var ids=java.util.List.of(c.getId());
        var counts=likes.likeCounts(ids);
        long count=counts.isEmpty()?0:((Number)counts.getFirst()[1]).longValue();
        boolean liked=viewer!=null&&likes.findByCommentIdAndStudentId(c.getId(),viewer.getId()).isPresent();
        var replies=comments.replyCounts(ids);
        return CommentResponse.from(c,viewer,count,liked,replies.isEmpty()?0:((Number)replies.getFirst()[1]).longValue());
    }
    private PostComment locked(Long id) {
        if (id == null || id <= 0) throw error(HttpStatus.BAD_REQUEST, "Mã bình luận không hợp lệ.");
        PostComment c = comments.findById(id).orElseThrow(() -> missing());
        // All mutations lock parent before comment, including publication/deletion, to avoid lock inversion.
        posts.findForUpdate(c.getPost().getId()).orElseThrow(() -> missing());
        // Refresh after the parent lock: a concurrent delete/moderation may have changed the earlier read.
        em.refresh(c, jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);
        return c;
    }
    private Post visibleLocked(Long id) {
        if (id == null || id <= 0) throw error(HttpStatus.BAD_REQUEST, "Mã bài viết không hợp lệ.");
        return posts.findForUpdate(id).filter(p -> p.isPublished() && !p.isDeleted()).orElseThrow(() -> missing());
    }
    private void requireEditable(PostComment c) {
        if(c.getParentId()!=null) root(c.getPost().getId(),c.getParentId());
        if (c.getStatus() != CommentStatus.VISIBLE || !c.getPost().isPublished() || c.getPost().isDeleted())
            throw error(HttpStatus.CONFLICT, "Chỉ được sửa/xóa khi bình luận và bài viết còn hiển thị.");
    }
    private String content(CommentRequest r) {
        if (r.content() == null || r.content().isBlank() || r.content().trim().length() > 2000) throw error(HttpStatus.BAD_REQUEST, "Bình luận cần có nội dung và tối đa 2000 ký tự.");
        return r.content().trim();
    }
    private User viewer(String email) { return email == null ? null : users.findByEmailIgnoreCase(email).orElse(null); }
    private ApiException missing() { return error(HttpStatus.NOT_FOUND, "Bài viết hoặc bình luận không khả dụng."); }
    private ApiException error(HttpStatus status, String message) { return new ApiException(status, message); }
    private void record(User actor, PostComment c, String action) {
        AuditLog a = new AuditLog(); a.setActor(actor); a.setAction(action); a.setTargetType("POST_COMMENT");
        a.setTargetId(c.getId().toString()); a.setOccurredAt(Instant.now()); audit.save(a);
    }
}

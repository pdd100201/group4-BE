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
        return BlogPage.from(comments.findByPostIdAndStatus(postId, CommentStatus.VISIBLE,
            PageRequest.of(page, size, Sort.by(Sort.Direction.ASC, "id"))).map(c -> CommentResponse.from(c, viewer)));
    }
    public CommentResponse detail(Long postId, Long id, String email) {
        publicPosts.visible(postId);
        if (id == null || id <= 0) throw error(HttpStatus.BAD_REQUEST, "Mã bình luận không hợp lệ.");
        PostComment c = comments.findByIdAndPostIdAndStatus(id, postId, CommentStatus.VISIBLE).orElseThrow(() -> missing());
        return CommentResponse.from(c, viewer(email));
    }
    public BlogPage<CommentResponse> moderationList(Long postId, String email, CommentStatus status, int page, int size) {
        User actor = actors.require(email, "MANAGER", "ADMIN");
        if (postId == null || postId <= 0 || page < 0 || size < 1 || size > 100)
            throw error(HttpStatus.BAD_REQUEST, "Mã bài viết hoặc phân trang không hợp lệ.");
        posts.findById(postId).filter(p -> !p.isDeleted()).orElseThrow(() -> missing());
        if (status == CommentStatus.DELETED) throw error(HttpStatus.BAD_REQUEST, "Bình luận đã xóa không nằm trong danh sách moderation.");
        return BlogPage.from(comments.findAll((root, query, cb) -> {
            if (query != null && query.getResultType() != Long.class) root.fetch("author");
            return cb.and(cb.equal(root.get("post").get("id"), postId), status == null
                ? cb.notEqual(root.get("status"), CommentStatus.DELETED) : cb.equal(root.get("status"), status));
        }, PageRequest.of(page, size, Sort.by(Sort.Direction.ASC, "id"))).map(c -> CommentResponse.from(c, actor)));
    }
    public CommentResponse moderationDetail(Long postId, Long id, String email) {
        User actor = actors.require(email, "MANAGER", "ADMIN");
        PostComment c = comments.findById(id).filter(comment -> comment.getPost().getId().equals(postId)
            && !comment.getPost().isDeleted() && comment.getStatus() != CommentStatus.DELETED).orElseThrow(() -> missing());
        return CommentResponse.from(c, actor);
    }
    @Transactional public CommentResponse create(Long postId, String email, CommentRequest request) {
        User student = actors.require(email, "STUDENT");
        Post post = visibleLocked(postId);
        PostComment c = new PostComment(); c.setPost(post); c.setAuthor(student); c.setContent(content(request));
        comments.saveAndFlush(c); record(student, c, "CREATE_COMMENT"); return CommentResponse.from(c, student);
    }
    @Transactional public CommentResponse update(Long id, String email, CommentRequest request) {
        User student = actors.require(email, "STUDENT");
        PostComment c = locked(id);
        if (!c.getAuthor().getId().equals(student.getId())) throw error(HttpStatus.FORBIDDEN, "Bạn chỉ được sửa bình luận của mình.");
        requireEditable(c); c.setContent(content(request)); c.setUpdatedAt(Instant.now());
        record(student, c, "UPDATE_COMMENT"); return CommentResponse.from(c, student);
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
        return CommentResponse.from(c, actor);
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

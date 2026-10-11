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
public class ManagerPostService {
    private final PostRepository posts;
    private final MediaAssetRepository media;
    private final AuditLogRepository audit;
    private final BlogActorService actors;

    public BlogPage<ManagedPostResponse> list(String email, int page, int size) {
        actors.require(email, "MANAGER");
        if (page < 0 || size < 1 || size > 100) throw new ApiException(HttpStatus.BAD_REQUEST, "Phân trang không hợp lệ.");
        return BlogPage.from(posts.findAll((root, query, cb) -> {
            if (query != null && query.getResultType() != Long.class) root.fetch("author");
            return cb.isFalse(root.get("deleted"));
        }, PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "updatedAt", "id"))).map(ManagedPostResponse::from));
    }
    public ManagedPostResponse detail(Long id, String email) {
        actors.require(email, "MANAGER");
        Post post = posts.findById(id).filter(p -> !p.isDeleted()).orElseThrow(() -> unavailable());
        return ManagedPostResponse.from(post);
    }
    @Transactional public ManagedPostResponse create(String email, PostRequest request) {
        User actor = actors.require(email, "MANAGER");
        Post post = new Post(); post.setAuthor(actor); apply(post, request, actor);
        posts.saveAndFlush(post); record(actor, post, "CREATE_POST");
        return ManagedPostResponse.from(post);
    }
    @Transactional public ManagedPostResponse update(Long id, String email, PostRequest request) {
        User actor = actors.require(email, "MANAGER");
        Post post = lock(id); apply(post, request, actor); record(actor, post, "UPDATE_POST");
        return ManagedPostResponse.from(post);
    }
    @Transactional public void delete(Long id, String email) {
        User actor = actors.require(email, "MANAGER");
        Post post = lock(id);
        // Retain the post, comments, media and audit history; do not cascade physical deletion.
        post.setDeleted(true); post.setPublished(false); post.setUpdatedAt(Instant.now());
        record(actor, post, "DELETE_POST");
    }
    private Post lock(Long id) {
        if (id == null || id <= 0) throw new ApiException(HttpStatus.BAD_REQUEST, "Mã bài viết không hợp lệ.");
        return posts.findForUpdate(id).filter(p -> !p.isDeleted()).orElseThrow(() -> unavailable());
    }
    private ApiException unavailable() { return new ApiException(HttpStatus.NOT_FOUND, "Không tìm thấy bài viết."); }
    private void apply(Post post, PostRequest r, User actor) {
        post.setTitle(r.title().trim()); post.setCategory(r.category() == null ? null : r.category().trim());
        post.setSummary(r.summary() == null ? null : r.summary().trim()); post.setContent(r.content().trim());
        if (r.thumbnailMediaId() != null) {
            MediaAsset asset = media.findById(r.thumbnailMediaId()).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Ảnh không tồn tại."));
            if (!"CONTENT_IMAGE".equals(asset.getPurpose()) || !"image".equals(asset.getResourceType()) || !asset.getOwner().getId().equals(actor.getId()))
                throw new ApiException(HttpStatus.FORBIDDEN, "Ảnh phải là ảnh nội dung do bạn tải lên.");
            post.setThumbnailUrl(asset.getSecureUrl());
        }
        if (Boolean.TRUE.equals(r.published()) && (post.getCategory() == null || post.getCategory().isBlank()
            || post.getSummary() == null || post.getSummary().isBlank() || post.getThumbnailUrl() == null || post.getThumbnailUrl().isBlank()))
            throw new ApiException(HttpStatus.CONFLICT, "Cần danh mục, tóm tắt và ảnh trước khi xuất bản bài viết.");
        post.setPublished(Boolean.TRUE.equals(r.published())); post.setUpdatedAt(Instant.now());
    }
    private void record(User actor, Post post, String action) {
        AuditLog entry = new AuditLog(); entry.setActor(actor); entry.setTargetType("POST");
        entry.setTargetId(post.getId().toString()); entry.setAction(action); entry.setOccurredAt(Instant.now()); audit.save(entry);
    }
}

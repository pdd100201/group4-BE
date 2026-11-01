package com.onlinelearning.service;

import com.onlinelearning.dto.blog.BlogPage;
import com.onlinelearning.dto.blog.ManagedPostResponse;
import com.onlinelearning.dto.blog.PostSubmissionRequest;
import com.onlinelearning.entity.*;
import com.onlinelearning.exception.ApiException;
import com.onlinelearning.repository.AuditLogRepository;
import com.onlinelearning.repository.MediaAssetRepository;
import com.onlinelearning.repository.PostRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;

@Service @RequiredArgsConstructor @Transactional(readOnly=true)
public class PostSubmissionService {
    private static final String[] SUBMITTER_ROLES = {"STUDENT", "EXPERT", "MANAGER", "ADMIN", "GUEST"};
    private final PostRepository posts;
    private final MediaAssetRepository media;
    private final AuditLogRepository audit;
    private final BlogActorService actors;

    @Transactional public ManagedPostResponse submit(String email, PostSubmissionRequest request) {
        User author = actors.require(email, SUBMITTER_ROLES);
        if (request.thumbnailMediaId() == null)
            throw new ApiException(HttpStatus.BAD_REQUEST, "Cần tải ảnh đại diện trước khi gửi bài.");
        MediaAsset image = media.findById(request.thumbnailMediaId())
            .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Ảnh không tồn tại."));
        if (!"CONTENT_IMAGE".equals(image.getPurpose()) || !"image".equals(image.getResourceType())
            || !image.getOwner().getId().equals(author.getId()))
            throw new ApiException(HttpStatus.FORBIDDEN, "Ảnh phải là ảnh nội dung do bạn tải lên.");

        Post post = new Post(); post.setAuthor(author); post.setTitle(request.title().trim());
        post.setCategory(request.category().trim()); post.setSummary(request.summary().trim());
        post.setContent(request.content().trim()); post.setThumbnailUrl(image.getSecureUrl());
        post.setPublished(false); post.setStatus(PostStatus.PENDING_REVIEW); post.setUpdatedAt(Instant.now());
        posts.saveAndFlush(post);
        record(author, post);
        return ManagedPostResponse.from(post);
    }

    public BlogPage<ManagedPostResponse> mine(String email, int page, int size) {
        User author = actors.require(email, SUBMITTER_ROLES);
        if (page < 0 || size < 1 || size > 100)
            throw new ApiException(HttpStatus.BAD_REQUEST, "Phân trang không hợp lệ.");
        return BlogPage.from(posts.findAll((root, query, cb) -> {
            if (query != null && query.getResultType() != Long.class) root.fetch("author");
            return cb.and(cb.equal(root.get("author").get("id"), author.getId()), cb.isFalse(root.get("deleted")));
        }, PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "updatedAt", "id"))).map(ManagedPostResponse::from));
    }

    private void record(User actor, Post post) {
        AuditLog entry = new AuditLog(); entry.setActor(actor); entry.setTargetType("POST");
        entry.setTargetId(post.getId().toString()); entry.setAction("SUBMIT_POST"); entry.setOccurredAt(Instant.now()); audit.save(entry);
    }
}

package com.onlinelearning.service;

import com.onlinelearning.dto.blog.*;
import com.onlinelearning.entity.Post;
import com.onlinelearning.exception.ApiException;
import com.onlinelearning.repository.PostRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

@Service @RequiredArgsConstructor @Transactional(readOnly=true)
public class PublicPostService {
    private final PostRepository posts;

    public BlogPage<PostResponse> list(String search, String category, int page, int size) {
        if (page < 0 || size < 1 || size > 100) throw new ApiException(HttpStatus.BAD_REQUEST, "Phân trang không hợp lệ.");
        if (search != null && search.length() > 255) throw new ApiException(HttpStatus.BAD_REQUEST, "Từ khóa tối đa 255 ký tự.");
        if (category != null && category.length() > 255) throw new ApiException(HttpStatus.BAD_REQUEST, "Danh mục không hợp lệ.");
        Specification<Post> spec = (root, query, cb) -> {
            // Fetch the display author in the page query without changing the count query.
            if (query != null && query.getResultType() != Long.class) root.fetch("author");
            return cb.and(cb.isTrue(root.get("published")), cb.isFalse(root.get("deleted")));
        };
        if (search != null && !search.isBlank()) {
            String pattern = "%" + search.trim().toLowerCase(java.util.Locale.ROOT)
                .replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
            spec = spec.and((root, query, cb) -> cb.like(cb.lower(root.get("title")), pattern, '\\'));
        }
        if (category != null && !category.isBlank()) spec = spec.and((root, query, cb) -> cb.equal(root.get("category"), category.trim()));
        return BlogPage.from(posts.findAll(spec, PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "updatedAt", "id"))).map(PostResponse::from));
    }

    public PostDetailResponse detail(Long id) { return PostDetailResponse.from(visible(id)); }
    public List<String> categories() { return posts.publicCategories(); }

    public Post visible(Long id) {
        if (id == null || id <= 0) throw new ApiException(HttpStatus.BAD_REQUEST, "Mã bài viết không hợp lệ.");
        return posts.findByIdAndPublishedTrueAndDeletedFalse(id)
            .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Bài viết không tồn tại hoặc không còn hiển thị."));
    }
}

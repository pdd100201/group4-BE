package com.onlinelearning.dto.blog;

import org.springframework.data.domain.Page;
import java.util.List;

public record BlogPage<T>(List<T> content, long totalElements, int totalPages, int number) {
    public static <T> BlogPage<T> from(Page<T> page) {
        return new BlogPage<>(page.getContent(), page.getTotalElements(), page.getTotalPages(), page.getNumber());
    }
}

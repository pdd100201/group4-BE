package com.onlinelearning.service;

import com.onlinelearning.exception.ApiException;
import com.onlinelearning.entity.Post;
import com.onlinelearning.repository.PostRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PublicPostServiceUnitTests {
    @Mock PostRepository posts;
    @InjectMocks PublicPostService publicPosts;

    @Test void rejectsInvalidPageAndPageSizeWithoutQueryingRepository() {
        assertBadRequest(() -> publicPosts.list(null, null, -1, 10));
        assertBadRequest(() -> publicPosts.list(null, null, 0, 0));
        assertBadRequest(() -> publicPosts.list(null, null, 0, 101));
        verifyNoInteractions(posts);
    }

    @Test void rejectsSearchAndCategoryLongerThan255Characters() {
        String oversized = "x".repeat(256);
        assertBadRequest(() -> publicPosts.list(oversized, null, 0, 10));
        assertBadRequest(() -> publicPosts.list(null, oversized, 0, 10));
        verifyNoInteractions(posts);
    }

    @Test void rejectsInvalidPostIdsBeforeRepositoryLookup() {
        assertBadRequest(() -> publicPosts.visible(null));
        assertBadRequest(() -> publicPosts.visible(0L));
        assertBadRequest(() -> publicPosts.visible(-1L));
        verifyNoInteractions(posts);
    }

    @Test void returnsTheVisiblePostFromTheRepository() {
        Post expected = new Post(); expected.setId(17L);
        when(posts.findByIdAndPublishedTrueAndDeletedFalse(17L)).thenReturn(Optional.of(expected));

        assertSame(expected, publicPosts.visible(17L));

        verify(posts).findByIdAndPublishedTrueAndDeletedFalse(17L);
    }

    @Test void mapsAnUnavailablePostToNotFound() {
        when(posts.findByIdAndPublishedTrueAndDeletedFalse(17L)).thenReturn(Optional.empty());

        ApiException error = assertThrows(ApiException.class, () -> publicPosts.visible(17L));

        assertEquals(404, error.status().value());
    }

    @Test void categoriesAreReadFromTheRepository() {
        when(posts.publicCategories()).thenReturn(List.of("Design", "Java"));

        assertEquals(List.of("Design", "Java"), publicPosts.categories());

        verify(posts).publicCategories();
    }

    @Test void acceptsMaximumSearchAndCategoryLengthsAndMaximumPageSize() {
        when(posts.findAll(org.mockito.ArgumentMatchers.<Specification<Post>>any(), any(Pageable.class)))
            .thenReturn(Page.<Post>empty());

        assertTrue(publicPosts.list("x".repeat(255), "c".repeat(255), 0, 1).content().isEmpty());
        assertTrue(publicPosts.list("x".repeat(255), "c".repeat(255), 0, 100).content().isEmpty());

        verify(posts, times(2)).findAll(org.mockito.ArgumentMatchers.<Specification<Post>>any(), any(Pageable.class));
    }

    private void assertBadRequest(org.junit.jupiter.api.function.Executable action) {
        ApiException error = assertThrows(ApiException.class, action);
        assertEquals(400, error.status().value());
    }
}

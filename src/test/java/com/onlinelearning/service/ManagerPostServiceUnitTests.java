package com.onlinelearning.service;

import com.onlinelearning.dto.blog.ManagedPostResponse;
import com.onlinelearning.dto.blog.PostRequest;
import com.onlinelearning.entity.MediaAsset;
import com.onlinelearning.entity.Post;
import com.onlinelearning.entity.User;
import com.onlinelearning.exception.ApiException;
import com.onlinelearning.repository.AuditLogRepository;
import com.onlinelearning.repository.MediaAssetRepository;
import com.onlinelearning.repository.PostRepository;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ManagerPostServiceUnitTests {
    private static final String MANAGER_EMAIL = "manager@example.com";

    @Mock PostRepository posts;
    @Mock MediaAssetRepository media;
    @Mock AuditLogRepository audit;
    @Mock BlogActorService actors;
    @InjectMocks ManagerPostService managerPosts;

    @Nested
    class CreateTests {
        @Test void createsAndTrimsADraft() {
            User manager = manager();
            when(actors.require(MANAGER_EMAIL, "MANAGER")).thenReturn(manager);
            when(posts.saveAndFlush(any())).thenAnswer(invocation -> {
                Post post = invocation.getArgument(0);
                post.setId(12L);
                return post;
            });

            ManagedPostResponse response = managerPosts.create(MANAGER_EMAIL,
                new PostRequest("  Draft title  ", null, null, "  Draft body  ", null, false));

            assertEquals("Draft title", response.post().title());
            assertEquals("Draft body", response.content());
            assertFalse(response.published());
            verify(audit).save(argThat(entry -> "CREATE_POST".equals(entry.getAction())));
        }

        @Test void publishesACompletePostWithManagersOwnImage() {
            User manager = manager();
            when(actors.require(MANAGER_EMAIL, "MANAGER")).thenReturn(manager);
            when(media.findById(3L)).thenReturn(Optional.of(imageOwnedBy(manager)));
            when(posts.saveAndFlush(any())).thenAnswer(invocation -> {
                Post post = invocation.getArgument(0);
                post.setId(12L);
                return post;
            });

            ManagedPostResponse response = managerPosts.create(MANAGER_EMAIL,
                new PostRequest("Title", " Java ", " Summary ", " Body ", 3L, true));

            assertTrue(response.published());
            assertEquals("Java", response.post().category());
            assertEquals("Summary", response.post().summary());
            assertEquals("https://example.test/post.png", response.post().thumbnailUrl());
            verify(audit).save(argThat(entry -> "CREATE_POST".equals(entry.getAction())));
        }

        @Test void rejectsPublishingWithoutCategorySummaryAndThumbnail() {
            when(actors.require(MANAGER_EMAIL, "MANAGER")).thenReturn(manager());

            ApiException error = assertThrows(ApiException.class, () -> managerPosts.create(MANAGER_EMAIL,
                new PostRequest("Title", " ", "Summary", "Body", null, true)));

            assertEquals(409, error.status().value());
            verifyNoInteractions(posts, audit);
        }

        @Test void rejectsPublishingWhenSummaryAloneIsMissing() {
            User manager = manager();
            when(actors.require(MANAGER_EMAIL, "MANAGER")).thenReturn(manager);
            when(media.findById(3L)).thenReturn(Optional.of(imageOwnedBy(manager)));

            ApiException error = assertThrows(ApiException.class, () -> managerPosts.create(MANAGER_EMAIL,
                new PostRequest("Title", "Java", "  ", "Body", 3L, true)));

            assertEquals(409, error.status().value());
            verifyNoInteractions(posts, audit);
        }

        @Test void rejectsPublishingWhenThumbnailAloneIsMissing() {
            when(actors.require(MANAGER_EMAIL, "MANAGER")).thenReturn(manager());

            ApiException error = assertThrows(ApiException.class, () -> managerPosts.create(MANAGER_EMAIL,
                new PostRequest("Title", "Java", "Summary", "Body", null, true)));

            assertEquals(409, error.status().value());
            verifyNoInteractions(posts, media, audit);
        }

        @Test void rejectsAnImageOwnedByAnotherManager() {
            User manager = manager();
            User other = user(8L, "Other Manager");
            when(actors.require(MANAGER_EMAIL, "MANAGER")).thenReturn(manager);
            when(media.findById(3L)).thenReturn(Optional.of(imageOwnedBy(other)));

            ApiException error = assertThrows(ApiException.class, () -> managerPosts.create(MANAGER_EMAIL,
                new PostRequest("Draft", null, null, "Body", 3L, false)));

            assertEquals(403, error.status().value());
            verifyNoInteractions(posts, audit);
        }
    }

    @Nested
    class ReadTests {
        @Test void listsPostsWithPageMetadataForAManager() {
            Post post = post(4L, "Blog post", false);
            when(actors.require(MANAGER_EMAIL, "MANAGER")).thenReturn(manager());
            when(posts.findAll(org.mockito.ArgumentMatchers.<Specification<Post>>any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(post), PageRequest.of(0, 10), 1));

            var result = managerPosts.list(MANAGER_EMAIL, 0, 10);

            assertEquals(1, result.totalElements());
            assertEquals(1, result.content().size());
            assertEquals("Blog post", result.content().get(0).post().title());
            verify(actors).require(MANAGER_EMAIL, "MANAGER");
        }

        @Test void rejectsInvalidPageBeforeQueryingPosts() {
            when(actors.require(MANAGER_EMAIL, "MANAGER")).thenReturn(manager());

            ApiException error = assertThrows(ApiException.class, () -> managerPosts.list(MANAGER_EMAIL, -1, 10));

            assertEquals(400, error.status().value());
            verifyNoInteractions(posts);
        }

        @Test void returnsAnExistingNonDeletedPost() {
            Post post = post(4L, "Blog post", true);
            when(actors.require(MANAGER_EMAIL, "MANAGER")).thenReturn(manager());
            when(posts.findById(4L)).thenReturn(Optional.of(post));

            ManagedPostResponse result = managerPosts.detail(4L, MANAGER_EMAIL);

            assertEquals(4L, result.post().id());
            assertTrue(result.published());
        }

        @Test void hidesMissingAndDeletedPosts() {
            Post deleted = post(4L, "Deleted post", false);
            deleted.setDeleted(true);
            when(actors.require(MANAGER_EMAIL, "MANAGER")).thenReturn(manager());
            when(posts.findById(4L)).thenReturn(Optional.of(deleted));
            when(posts.findById(5L)).thenReturn(Optional.empty());

            assertEquals(404, assertThrows(ApiException.class,
                () -> managerPosts.detail(4L, MANAGER_EMAIL)).status().value());
            assertEquals(404, assertThrows(ApiException.class,
                () -> managerPosts.detail(5L, MANAGER_EMAIL)).status().value());
        }
    }

    @Nested
    class UpdateTests {
        @Test void updatesAndTrimsPostFields() {
            User manager = manager();
            Post post = post(4L, "Old title", false);
            when(actors.require(MANAGER_EMAIL, "MANAGER")).thenReturn(manager);
            when(posts.findForUpdate(4L)).thenReturn(Optional.of(post));

            ManagedPostResponse result = managerPosts.update(4L, MANAGER_EMAIL,
                new PostRequest(" New title ", " Java ", " Summary ", " New body ", null, false));

            assertEquals("New title", result.post().title());
            assertEquals("Java", result.post().category());
            assertEquals("Summary", result.post().summary());
            assertEquals("New body", result.content());
            verify(audit).save(argThat(entry -> "UPDATE_POST".equals(entry.getAction())));
        }

        @Test void preservesExistingThumbnailWhenNoReplacementIsSelected() {
            User manager = manager();
            Post post = post(4L, "Old title", false);
            post.setThumbnailUrl("https://example.test/current.png");
            when(actors.require(MANAGER_EMAIL, "MANAGER")).thenReturn(manager);
            when(posts.findForUpdate(4L)).thenReturn(Optional.of(post));

            var result = managerPosts.update(4L, MANAGER_EMAIL,
                new PostRequest("Updated", null, null, "Body", null, false));

            assertEquals("https://example.test/current.png", result.post().thumbnailUrl());
            verifyNoInteractions(media);
        }

        @Test void rejectsAnotherManagersImageWithoutWritingAudit() {
            User manager = manager();
            Post post = post(4L, "Old title", false);
            when(actors.require(MANAGER_EMAIL, "MANAGER")).thenReturn(manager);
            when(posts.findForUpdate(4L)).thenReturn(Optional.of(post));
            when(media.findById(3L)).thenReturn(Optional.of(imageOwnedBy(user(8L, "Other Manager"))));

            ApiException error = assertThrows(ApiException.class, () -> managerPosts.update(4L, MANAGER_EMAIL,
                new PostRequest("Updated", null, null, "Body", 3L, false)));

            assertEquals(403, error.status().value());
            verifyNoInteractions(audit);
        }

        @Test void returnsNotFoundWhenUpdatingMissingOrDeletedPost() {
            Post deleted = post(4L, "Deleted", false);
            deleted.setDeleted(true);
            when(actors.require(MANAGER_EMAIL, "MANAGER")).thenReturn(manager());
            when(posts.findForUpdate(4L)).thenReturn(Optional.of(deleted));
            when(posts.findForUpdate(5L)).thenReturn(Optional.empty());
            PostRequest request = new PostRequest("Updated", null, null, "Body", null, false);

            assertEquals(404, assertThrows(ApiException.class,
                () -> managerPosts.update(4L, MANAGER_EMAIL, request)).status().value());
            assertEquals(404, assertThrows(ApiException.class,
                () -> managerPosts.update(5L, MANAGER_EMAIL, request)).status().value());
            verifyNoInteractions(audit);
        }
    }

    @Nested
    class DeleteTests {
        @Test void softDeletesAndUnpublishesThePost() {
            Post post = post(4L, "Blog post", true);
            when(actors.require(MANAGER_EMAIL, "MANAGER")).thenReturn(manager());
            when(posts.findForUpdate(4L)).thenReturn(Optional.of(post));

            managerPosts.delete(4L, MANAGER_EMAIL);

            assertTrue(post.isDeleted());
            assertFalse(post.isPublished());
            verify(audit).save(argThat(entry -> "DELETE_POST".equals(entry.getAction())));
            verify(posts, never()).delete(any(Post.class));
        }

        @Test void rejectsInvalidPostIdBeforeLoadingPost() {
            when(actors.require(MANAGER_EMAIL, "MANAGER")).thenReturn(manager());

            ApiException error = assertThrows(ApiException.class, () -> managerPosts.delete(0L, MANAGER_EMAIL));

            assertEquals(400, error.status().value());
            verifyNoInteractions(posts, audit);
        }

        @Test void returnsNotFoundForMissingPost() {
            when(actors.require(MANAGER_EMAIL, "MANAGER")).thenReturn(manager());
            when(posts.findForUpdate(4L)).thenReturn(Optional.empty());

            ApiException error = assertThrows(ApiException.class, () -> managerPosts.delete(4L, MANAGER_EMAIL));

            assertEquals(404, error.status().value());
            verifyNoInteractions(audit);
        }

        @Test void doesNotDeleteAnAlreadySoftDeletedPostTwice() {
            Post deleted = post(4L, "Deleted post", false);
            deleted.setDeleted(true);
            when(actors.require(MANAGER_EMAIL, "MANAGER")).thenReturn(manager());
            when(posts.findForUpdate(4L)).thenReturn(Optional.of(deleted));

            ApiException error = assertThrows(ApiException.class, () -> managerPosts.delete(4L, MANAGER_EMAIL));

            assertEquals(404, error.status().value());
            verifyNoInteractions(audit);
        }
    }

    private User manager() {
        return user(7L, "Manager");
    }

    private User user(Long id, String name) {
        User user = new User();
        user.setId(id);
        user.setFullName(name);
        user.setEnabled(true);
        return user;
    }

    private Post post(Long id, String title, boolean published) {
        Post post = new Post();
        post.setId(id);
        post.setAuthor(manager());
        post.setTitle(title);
        post.setContent("Post body");
        post.setPublished(published);
        post.setDeleted(false);
        return post;
    }

    private MediaAsset imageOwnedBy(User owner) {
        MediaAsset image = new MediaAsset();
        image.setId(3L);
        image.setOwner(owner);
        image.setPurpose("CONTENT_IMAGE");
        image.setResourceType("image");
        image.setSecureUrl("https://example.test/post.png");
        return image;
    }
}

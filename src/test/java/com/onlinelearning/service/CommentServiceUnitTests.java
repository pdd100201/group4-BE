package com.onlinelearning.service;

import com.onlinelearning.dto.blog.CommentRequest;
import com.onlinelearning.entity.CommentStatus;
import com.onlinelearning.entity.Post;
import com.onlinelearning.entity.PostComment;
import com.onlinelearning.entity.Role;
import com.onlinelearning.entity.User;
import com.onlinelearning.exception.ApiException;
import com.onlinelearning.repository.AuditLogRepository;
import com.onlinelearning.repository.PostCommentRepository;
import com.onlinelearning.repository.PostRepository;
import com.onlinelearning.repository.UserRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CommentServiceUnitTests {
    private static final String STUDENT_EMAIL = "student@example.com";
    private static final String OTHER_STUDENT_EMAIL = "other@example.com";
    private static final String MANAGER_EMAIL = "manager@example.com";
    private static final long POST_ID = 9L;
    private static final long COMMENT_ID = 22L;

    @Mock PostCommentRepository comments;
    @Mock PostRepository posts;
    @Mock UserRepository users;
    @Mock PublicPostService publicPosts;
    @Mock BlogActorService actors;
    @Mock AuditLogRepository audit;
    @Mock EntityManager entityManager;
    @InjectMocks CommentService commentService;

    @Nested
    class CreateTests {
        @Test void createsVisibleCommentForStudentAndTrimsContent() {
            User student = actor(4L, "STUDENT");
            when(actors.require(STUDENT_EMAIL, "STUDENT")).thenReturn(student);
            when(posts.findForUpdate(POST_ID)).thenReturn(Optional.of(visiblePost(POST_ID)));
            when(comments.saveAndFlush(any())).thenAnswer(invocation -> {
                PostComment saved = invocation.getArgument(0);
                saved.setId(COMMENT_ID);
                return saved;
            });

            var response = commentService.create(POST_ID, STUDENT_EMAIL, new CommentRequest("  Hello  "));

            assertEquals("Hello", response.content());
            assertEquals("Student", response.authorName());
            assertTrue(response.canEdit());
            verify(comments).saveAndFlush(argThat(comment -> "Hello".equals(comment.getContent())
                && comment.getAuthor().getId().equals(4L)));
            verify(audit).save(argThat(entry -> "CREATE_COMMENT".equals(entry.getAction())));
        }

        @Test void rejectsBlankCommentWithoutSaving() {
            when(actors.require(STUDENT_EMAIL, "STUDENT")).thenReturn(actor(4L, "STUDENT"));
            when(posts.findForUpdate(POST_ID)).thenReturn(Optional.of(visiblePost(POST_ID)));

            ApiException error = assertThrows(ApiException.class, () -> commentService.create(POST_ID,
                STUDENT_EMAIL, new CommentRequest("  \n ")));

            assertEquals(400, error.status().value());
            verifyNoInteractions(comments, audit);
        }

        @Test void rejectsCommentLongerThan2000Characters() {
            when(actors.require(STUDENT_EMAIL, "STUDENT")).thenReturn(actor(4L, "STUDENT"));
            when(posts.findForUpdate(POST_ID)).thenReturn(Optional.of(visiblePost(POST_ID)));

            ApiException error = assertThrows(ApiException.class, () -> commentService.create(POST_ID,
                STUDENT_EMAIL, new CommentRequest("x".repeat(2001))));

            assertEquals(400, error.status().value());
            verifyNoInteractions(comments, audit);
        }

        @Test void acceptsCommentAtThe2000CharacterLimit() {
            User student = actor(4L, "STUDENT");
            when(actors.require(STUDENT_EMAIL, "STUDENT")).thenReturn(student);
            when(posts.findForUpdate(POST_ID)).thenReturn(Optional.of(visiblePost(POST_ID)));
            when(comments.saveAndFlush(any())).thenAnswer(invocation -> {
                PostComment saved = invocation.getArgument(0);
                saved.setId(COMMENT_ID);
                return saved;
            });
            String content = "x".repeat(2000);

            var response = commentService.create(POST_ID, STUDENT_EMAIL, new CommentRequest(content));

            assertEquals(2000, response.content().length());
            assertEquals(content, response.content());
            verify(audit).save(argThat(entry -> "CREATE_COMMENT".equals(entry.getAction())));
        }

        @Test void rejectsCommentWhenPostIsNotPublic() {
            when(actors.require(STUDENT_EMAIL, "STUDENT")).thenReturn(actor(4L, "STUDENT"));
            when(posts.findForUpdate(POST_ID)).thenReturn(Optional.empty());

            ApiException error = assertThrows(ApiException.class, () -> commentService.create(POST_ID,
                STUDENT_EMAIL, new CommentRequest("Hello")));

            assertEquals(404, error.status().value());
            verifyNoInteractions(comments, audit);
        }
    }

    @Nested
    class ReadTests {
        @Test void listsVisibleCommentsAndMarksTheOwnersPermissions() {
            User student = actor(4L, "STUDENT");
            PostComment comment = comment(COMMENT_ID, visiblePost(POST_ID), student, CommentStatus.VISIBLE);
            when(comments.findByPostIdAndStatus(eq(POST_ID), eq(CommentStatus.VISIBLE), any()))
                .thenReturn(new PageImpl<>(List.of(comment), PageRequest.of(0, 10), 1));
            when(users.findByEmailIgnoreCase(STUDENT_EMAIL)).thenReturn(Optional.of(student));

            var page = commentService.list(POST_ID, STUDENT_EMAIL, 0, 10);

            assertEquals(1, page.totalElements());
            assertEquals("Original", page.content().get(0).content());
            assertTrue(page.content().get(0).canEdit());
        }

        @Test void listsVisibleCommentsForGuestsWithoutEditPermission() {
            User student = actor(4L, "STUDENT");
            PostComment comment = comment(COMMENT_ID, visiblePost(POST_ID), student, CommentStatus.VISIBLE);
            when(comments.findByPostIdAndStatus(eq(POST_ID), eq(CommentStatus.VISIBLE), any()))
                .thenReturn(new PageImpl<>(List.of(comment), PageRequest.of(0, 10), 1));

            var page = commentService.list(POST_ID, null, 0, 10);

            assertEquals(1, page.content().size());
            assertFalse(page.content().get(0).canEdit());
            verifyNoInteractions(users);
        }

        @Test void rejectsInvalidCommentPaginationWithoutQueryingComments() {
            ApiException error = assertThrows(ApiException.class, () -> commentService.list(POST_ID,
                null, -1, 10));

            assertEquals(400, error.status().value());
            verifyNoInteractions(comments);
        }

        @Test void returnsOnlyAVisibleCommentFromTheRequestedPost() {
            User student = actor(4L, "STUDENT");
            PostComment comment = comment(COMMENT_ID, visiblePost(POST_ID), student, CommentStatus.VISIBLE);
            when(comments.findByIdAndPostIdAndStatus(COMMENT_ID, POST_ID, CommentStatus.VISIBLE))
                .thenReturn(Optional.of(comment));
            when(users.findByEmailIgnoreCase(STUDENT_EMAIL)).thenReturn(Optional.of(student));

            var response = commentService.detail(POST_ID, COMMENT_ID, STUDENT_EMAIL);

            assertEquals(COMMENT_ID, response.id());
            assertEquals(POST_ID, response.postId());
            assertTrue(response.canEdit());
        }
    }

    @Nested
    class UpdateTests {
        @Test void ownerCanUpdateVisibleCommentAndAuditTheChange() {
            User student = actor(4L, "STUDENT");
            Post post = visiblePost(POST_ID);
            PostComment comment = comment(COMMENT_ID, post, student, CommentStatus.VISIBLE);
            when(actors.require(STUDENT_EMAIL, "STUDENT")).thenReturn(student);
            when(comments.findById(COMMENT_ID)).thenReturn(Optional.of(comment));
            when(posts.findForUpdate(POST_ID)).thenReturn(Optional.of(post));

            var response = commentService.update(COMMENT_ID, STUDENT_EMAIL, new CommentRequest("  Edited  "));

            assertEquals("Edited", response.content());
            assertEquals(CommentStatus.VISIBLE, response.status());
            assertTrue(response.canEdit());
            verify(entityManager).refresh(comment, LockModeType.PESSIMISTIC_WRITE);
            verify(audit).save(argThat(entry -> "UPDATE_COMMENT".equals(entry.getAction())));
        }

        @Test void anotherStudentCannotUpdateTheOwnersComment() {
            User student = actor(5L, "STUDENT");
            PostComment comment = comment(COMMENT_ID, visiblePost(POST_ID), actor(4L, "STUDENT"), CommentStatus.VISIBLE);
            when(actors.require(OTHER_STUDENT_EMAIL, "STUDENT")).thenReturn(student);
            when(comments.findById(COMMENT_ID)).thenReturn(Optional.of(comment));
            when(posts.findForUpdate(POST_ID)).thenReturn(Optional.of(comment.getPost()));

            ApiException error = assertThrows(ApiException.class, () -> commentService.update(COMMENT_ID,
                OTHER_STUDENT_EMAIL, new CommentRequest("Denied")));

            assertEquals(403, error.status().value());
            verifyNoInteractions(audit);
        }

        @Test void ownerCannotUpdateHiddenComment() {
            User student = actor(4L, "STUDENT");
            Post post = visiblePost(POST_ID);
            PostComment comment = comment(COMMENT_ID, post, student, CommentStatus.HIDDEN);
            when(actors.require(STUDENT_EMAIL, "STUDENT")).thenReturn(student);
            when(comments.findById(COMMENT_ID)).thenReturn(Optional.of(comment));
            when(posts.findForUpdate(POST_ID)).thenReturn(Optional.of(post));

            ApiException error = assertThrows(ApiException.class, () -> commentService.update(COMMENT_ID,
                STUDENT_EMAIL, new CommentRequest("Denied")));

            assertEquals(409, error.status().value());
            verifyNoInteractions(audit);
        }

        @Test void rejectsBlankReplacementContent() {
            User student = actor(4L, "STUDENT");
            Post post = visiblePost(POST_ID);
            PostComment comment = comment(COMMENT_ID, post, student, CommentStatus.VISIBLE);
            when(actors.require(STUDENT_EMAIL, "STUDENT")).thenReturn(student);
            when(comments.findById(COMMENT_ID)).thenReturn(Optional.of(comment));
            when(posts.findForUpdate(POST_ID)).thenReturn(Optional.of(post));

            ApiException error = assertThrows(ApiException.class, () -> commentService.update(COMMENT_ID,
                STUDENT_EMAIL, new CommentRequest("  ")));

            assertEquals(400, error.status().value());
            verifyNoInteractions(audit);
        }
    }

    @Nested
    class DeleteTests {
        @Test void ownerCanSoftDeleteTheirVisibleComment() {
            User student = actor(4L, "STUDENT");
            Post post = visiblePost(POST_ID);
            PostComment comment = comment(COMMENT_ID, post, student, CommentStatus.VISIBLE);
            when(actors.require(STUDENT_EMAIL, "STUDENT", "MANAGER", "ADMIN")).thenReturn(student);
            when(comments.findById(COMMENT_ID)).thenReturn(Optional.of(comment));
            when(posts.findForUpdate(POST_ID)).thenReturn(Optional.of(post));

            commentService.delete(COMMENT_ID, STUDENT_EMAIL);

            assertEquals(CommentStatus.DELETED, comment.getStatus());
            verify(audit).save(argThat(entry -> "DELETE_COMMENT".equals(entry.getAction())));
            verify(comments, never()).delete(any(PostComment.class));
        }

        @Test void studentCannotDeleteAnotherStudentsComment() {
            User student = actor(5L, "STUDENT");
            Post post = visiblePost(POST_ID);
            PostComment comment = comment(COMMENT_ID, post, actor(4L, "STUDENT"), CommentStatus.VISIBLE);
            when(actors.require(OTHER_STUDENT_EMAIL, "STUDENT", "MANAGER", "ADMIN")).thenReturn(student);
            when(comments.findById(COMMENT_ID)).thenReturn(Optional.of(comment));
            when(posts.findForUpdate(POST_ID)).thenReturn(Optional.of(post));

            ApiException error = assertThrows(ApiException.class,
                () -> commentService.delete(COMMENT_ID, OTHER_STUDENT_EMAIL));

            assertEquals(403, error.status().value());
            assertEquals(CommentStatus.VISIBLE, comment.getStatus());
            verifyNoInteractions(audit);
        }

        @Test void moderatorCanSoftDeleteAnotherUsersHiddenComment() {
            User manager = actor(7L, "MANAGER");
            Post post = visiblePost(POST_ID);
            PostComment comment = comment(COMMENT_ID, post, actor(4L, "STUDENT"), CommentStatus.HIDDEN);
            when(actors.require(MANAGER_EMAIL, "STUDENT", "MANAGER", "ADMIN")).thenReturn(manager);
            when(comments.findById(COMMENT_ID)).thenReturn(Optional.of(comment));
            when(posts.findForUpdate(POST_ID)).thenReturn(Optional.of(post));

            commentService.delete(COMMENT_ID, MANAGER_EMAIL);

            assertEquals(CommentStatus.DELETED, comment.getStatus());
            verify(audit).save(argThat(entry -> "DELETE_COMMENT".equals(entry.getAction())));
        }

        @Test void doesNotDeleteAnAlreadyDeletedCommentTwice() {
            User student = actor(4L, "STUDENT");
            Post post = visiblePost(POST_ID);
            PostComment comment = comment(COMMENT_ID, post, student, CommentStatus.DELETED);
            when(actors.require(STUDENT_EMAIL, "STUDENT", "MANAGER", "ADMIN")).thenReturn(student);
            when(comments.findById(COMMENT_ID)).thenReturn(Optional.of(comment));
            when(posts.findForUpdate(POST_ID)).thenReturn(Optional.of(post));

            ApiException error = assertThrows(ApiException.class,
                () -> commentService.delete(COMMENT_ID, STUDENT_EMAIL));

            assertEquals(409, error.status().value());
            verifyNoInteractions(audit);
        }
    }

    @Nested
    class ModerationTests {
        @Test void moderatorCanChangeCommentStatusAndRecordAudit() {
            User manager = actor(7L, "MANAGER");
            Post post = visiblePost(POST_ID);
            PostComment comment = comment(COMMENT_ID, post, actor(4L, "STUDENT"), CommentStatus.VISIBLE);
            when(actors.require(MANAGER_EMAIL, "MANAGER", "ADMIN")).thenReturn(manager);
            when(comments.findById(COMMENT_ID)).thenReturn(Optional.of(comment));
            when(posts.findForUpdate(POST_ID)).thenReturn(Optional.of(post));

            var response = commentService.status(COMMENT_ID, MANAGER_EMAIL, CommentStatus.HIDDEN);

            assertEquals(CommentStatus.HIDDEN, response.status());
            assertFalse(response.canEdit());
            verify(audit).save(argThat(entry -> "UPDATE_COMMENT_STATUS".equals(entry.getAction())));
        }

        @Test void rejectsRepeatedModerationStatusAsConflict() {
            User manager = actor(7L, "MANAGER");
            Post post = visiblePost(POST_ID);
            PostComment comment = comment(COMMENT_ID, post, actor(4L, "STUDENT"), CommentStatus.HIDDEN);
            when(actors.require(MANAGER_EMAIL, "MANAGER", "ADMIN")).thenReturn(manager);
            when(comments.findById(COMMENT_ID)).thenReturn(Optional.of(comment));
            when(posts.findForUpdate(POST_ID)).thenReturn(Optional.of(post));

            ApiException error = assertThrows(ApiException.class,
                () -> commentService.status(COMMENT_ID, MANAGER_EMAIL, CommentStatus.HIDDEN));

            assertEquals(409, error.status().value());
            verifyNoInteractions(audit);
        }

        @Test void doesNotAllowDeletedAsAStatusTransition() {
            User manager = actor(7L, "MANAGER");
            Post post = visiblePost(POST_ID);
            PostComment comment = comment(COMMENT_ID, post, actor(4L, "STUDENT"), CommentStatus.VISIBLE);
            when(actors.require(MANAGER_EMAIL, "MANAGER", "ADMIN")).thenReturn(manager);
            when(comments.findById(COMMENT_ID)).thenReturn(Optional.of(comment));
            when(posts.findForUpdate(POST_ID)).thenReturn(Optional.of(post));

            ApiException error = assertThrows(ApiException.class,
                () -> commentService.status(COMMENT_ID, MANAGER_EMAIL, CommentStatus.DELETED));

            assertEquals(400, error.status().value());
            verifyNoInteractions(audit);
        }

        @Test void deniesStudentBeforeLoadingCommentForModeration() {
            ApiException forbidden = new ApiException(HttpStatus.FORBIDDEN, "Manager or admin role required.");
            when(actors.require(STUDENT_EMAIL, "MANAGER", "ADMIN")).thenThrow(forbidden);

            ApiException error = assertThrows(ApiException.class,
                () -> commentService.status(COMMENT_ID, STUDENT_EMAIL, CommentStatus.HIDDEN));

            assertSame(forbidden, error);
            verifyNoInteractions(comments, posts, audit, entityManager);
        }

        @Test void rejectsModerationWhenParentPostIsDeleted() {
            User manager = actor(7L, "MANAGER");
            Post deletedPost = visiblePost(POST_ID);
            deletedPost.setDeleted(true);
            PostComment comment = comment(COMMENT_ID, deletedPost, actor(4L, "STUDENT"), CommentStatus.VISIBLE);
            when(actors.require(MANAGER_EMAIL, "MANAGER", "ADMIN")).thenReturn(manager);
            when(comments.findById(COMMENT_ID)).thenReturn(Optional.of(comment));
            when(posts.findForUpdate(POST_ID)).thenReturn(Optional.of(deletedPost));

            ApiException error = assertThrows(ApiException.class,
                () -> commentService.status(COMMENT_ID, MANAGER_EMAIL, CommentStatus.HIDDEN));

            assertEquals(409, error.status().value());
            assertEquals(CommentStatus.VISIBLE, comment.getStatus());
            verifyNoInteractions(audit);
        }
    }

    private User actor(Long id, String roleCode) {
        Role role = new Role();
        role.setCode(roleCode);
        User user = new User();
        user.setId(id);
        user.setEmail(roleCode.toLowerCase() + "@example.com");
        user.setFullName(roleCode.equals("STUDENT") ? "Student" : roleCode);
        user.setEnabled(true);
        user.getRoles().add(role);
        return user;
    }

    private Post visiblePost(Long id) {
        Post post = new Post();
        post.setId(id);
        post.setTitle("Public post");
        post.setPublished(true);
        post.setDeleted(false);
        return post;
    }

    private PostComment comment(Long id, Post post, User author, CommentStatus status) {
        PostComment comment = new PostComment();
        comment.setId(id);
        comment.setPost(post);
        comment.setAuthor(author);
        comment.setContent("Original");
        comment.setStatus(status);
        return comment;
    }
}

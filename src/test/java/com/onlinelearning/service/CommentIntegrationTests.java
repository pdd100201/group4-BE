package com.onlinelearning.service;

import com.onlinelearning.entity.*;
import com.onlinelearning.repository.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.MediaType;
import java.time.Instant;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest @ActiveProfiles("test") @AutoConfigureMockMvc @Transactional
class CommentIntegrationTests {
    @Autowired MockMvc mvc;
    @Autowired PostRepository posts;
    @Autowired PostCommentRepository comments;
    @Autowired UserRepository users;
    @Autowired AuditLogRepository audit;
    private static final String STUDENT = "student@onlinelearning.local";
    private Post createPost(boolean visible) {
        Post p = new Post(); p.setAuthor(users.findByEmailIgnoreCase("manager@onlinelearning.local").orElseThrow());
        p.setTitle("Bài viết"); p.setContent("Nội dung"); p.setPublished(visible); return posts.saveAndFlush(p);
    }
    private PostComment comment(Post p, CommentStatus status, String owner) {
        PostComment c = new PostComment(); c.setPost(p); c.setAuthor(users.findByEmailIgnoreCase(owner).orElseThrow());
        c.setContent("Bình luận"); c.setStatus(status); return comments.saveAndFlush(c);
    }
    private String publicUrl(Post p) { return "/api/v1/public/posts/" + p.getId() + "/comments"; }
    private User otherStudent() {
        User original = users.findByEmailIgnoreCase(STUDENT).orElseThrow(); User other = new User();
        other.setEmail("other-student@test.local"); other.setPasswordHash(original.getPasswordHash()); other.setFullName("Học viên khác");
        other.setEnabled(true); other.setRoles(new java.util.HashSet<>(original.getRoles())); return users.saveAndFlush(other);
    }
    @Test void uc60OnlyVisibleCommentsOfPublicPostsWithDatabasePaging() throws Exception {
        Post p = createPost(true);
        for (CommentStatus status : CommentStatus.values()) comment(p, status, STUDENT);
        comment(p, CommentStatus.VISIBLE, STUDENT);
        mvc.perform(get(publicUrl(p)).param("size", "1")).andExpect(status().isOk()).andExpect(jsonPath("totalElements").value(2))
            .andExpect(jsonPath("totalPages").value(2)).andExpect(jsonPath("content[0].canEdit").value(false));
        mvc.perform(get(publicUrl(p)).param("page", "-1")).andExpect(status().isBadRequest());
        p.setPublished(false); posts.flush(); mvc.perform(get(publicUrl(p))).andExpect(status().isNotFound());
    }
    @Test void uc61DirectIdsCannotLeakHiddenCommentsOrOtherPostOrPrivateAuthorFields() throws Exception {
        Post p = createPost(true); Post other = createPost(true); PostComment c = comment(p, CommentStatus.VISIBLE, STUDENT);
        mvc.perform(get(publicUrl(p) + "/" + c.getId())).andExpect(status().isOk())
            .andExpect(jsonPath("postTitle").value("Bài viết")).andExpect(jsonPath("authorName").exists())
            .andExpect(jsonPath("author.email").doesNotExist()).andExpect(jsonPath("author.passwordHash").doesNotExist());
        mvc.perform(get(publicUrl(other) + "/" + c.getId())).andExpect(status().isNotFound());
        for (CommentStatus state : new CommentStatus[]{CommentStatus.HIDDEN, CommentStatus.DISABLED, CommentStatus.DELETED}) {
            c.setStatus(state); comments.flush(); mvc.perform(get(publicUrl(p) + "/" + c.getId())).andExpect(status().isNotFound());
        }
        p.setDeleted(true); posts.flush(); mvc.perform(get(publicUrl(p) + "/" + c.getId())).andExpect(status().isNotFound());
    }
    @Test void uc62CreatesOnlyForStudentAndValidVisiblePostAndValidContent() throws Exception {
        Post p = createPost(true); String url = "/api/v1/student/posts/" + p.getId() + "/comments";
        mvc.perform(post(url).contentType(MediaType.APPLICATION_JSON).content("{\"content\":\"Hello\"}")).andExpect(status().isUnauthorized());
        for (String role : new String[]{"EXPERT", "MANAGER", "ADMIN"}) mvc.perform(post(url).with(user(role.toLowerCase()+"@onlinelearning.local").roles(role))
            .contentType(MediaType.APPLICATION_JSON).content("{\"content\":\"Hello\"}")).andExpect(status().isForbidden());
        mvc.perform(post(url).with(user(STUDENT).roles("STUDENT")).contentType(MediaType.APPLICATION_JSON).content("{\"content\":\" Hello \",\"authorId\":999}"))
            .andExpect(status().isCreated()).andExpect(jsonPath("content").value("Hello")).andExpect(jsonPath("authorName").value("Online Learning Student"));
        for (String invalid : new String[]{" ", "x".repeat(2001)}) mvc.perform(post(url).with(user(STUDENT).roles("STUDENT"))
            .contentType(MediaType.APPLICATION_JSON).content("{\"content\":\""+invalid+"\"}")).andExpect(status().isBadRequest());
        p.setPublished(false); posts.flush(); mvc.perform(post(url).with(user(STUDENT).roles("STUDENT"))
            .contentType(MediaType.APPLICATION_JSON).content("{\"content\":\"Hello\"}")).andExpect(status().isNotFound());
    }
    @Test void uc63OwnCommentCanBeEditedWithoutTimeLimitButHiddenAndWrongOwnerAreDenied() throws Exception {
        Post p = createPost(true); PostComment c = comment(p, CommentStatus.VISIBLE, STUDENT);
        c.setCreatedAt(Instant.parse("2024-01-01T00:00:00Z")); comments.flush();
        mvc.perform(put("/api/v1/student/comments/" + c.getId()).with(user(STUDENT).roles("STUDENT"))
            .contentType(MediaType.APPLICATION_JSON).content("{\"content\":\"Đã chỉnh sửa\"}"))
            .andExpect(status().isOk()).andExpect(jsonPath("content").value("Đã chỉnh sửa")).andExpect(jsonPath("canEdit").value(true));
        User other = otherStudent();
        mvc.perform(put("/api/v1/student/comments/" + c.getId()).with(user(other.getEmail()).roles("STUDENT"))
            .contentType(MediaType.APPLICATION_JSON).content("{\"content\":\"Denied\"}")).andExpect(status().isForbidden());
        c.setStatus(CommentStatus.HIDDEN); comments.flush();
        mvc.perform(put("/api/v1/student/comments/" + c.getId()).with(user(STUDENT).roles("STUDENT"))
            .contentType(MediaType.APPLICATION_JSON).content("{\"content\":\"Denied\"}")).andExpect(status().isConflict());
    }
    @Test void uc64StudentOwnAndManagerAdminDeletionRetainsDataAndAudit() throws Exception {
        Post p = createPost(true); PostComment c = comment(p, CommentStatus.VISIBLE, STUDENT); User other = otherStudent();
        mvc.perform(delete("/api/v1/comments/" + c.getId()).with(user(other.getEmail()).roles("STUDENT"))).andExpect(status().isForbidden());
        mvc.perform(delete("/api/v1/comments/" + c.getId()).with(user(STUDENT).roles("STUDENT"))).andExpect(status().isNoContent());
        assertThat(comments.findById(c.getId()).orElseThrow().getStatus()).isEqualTo(CommentStatus.DELETED);
        mvc.perform(get(publicUrl(p) + "/" + c.getId())).andExpect(status().isNotFound());
        for (String role : new String[]{"MANAGER", "ADMIN"}) {
            PostComment hidden = comment(p, CommentStatus.HIDDEN, STUDENT);
            mvc.perform(delete("/api/v1/comments/" + hidden.getId()).with(user(role.toLowerCase()+"@onlinelearning.local").roles(role))).andExpect(status().isNoContent());
        }
        assertThat(audit.findAll()).anyMatch(a -> "DELETE_COMMENT".equals(a.getAction()));
    }
    @Test void uc65OnlyModeratorsCanHideDisableAndRestoreAndDeletedIsTerminal() throws Exception {
        Post p = createPost(true); PostComment c = comment(p, CommentStatus.VISIBLE, STUDENT); String url = "/api/v1/comments/" + c.getId() + "/status";
        for (String role : new String[]{"STUDENT", "EXPERT"}) mvc.perform(patch(url).with(user(role.toLowerCase()+"@onlinelearning.local").roles(role))
            .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"HIDDEN\"}")).andExpect(status().isForbidden());
        for (String state : new String[]{"HIDDEN", "DISABLED", "VISIBLE"}) {
            mvc.perform(patch(url).with(user("manager@onlinelearning.local").roles("MANAGER"))
                .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\""+state+"\"}")).andExpect(status().isOk());
            mvc.perform(get(publicUrl(p))).andExpect(jsonPath("totalElements").value(state.equals("VISIBLE") ? 1 : 0));
        }
        mvc.perform(patch(url).with(user("admin@onlinelearning.local").roles("ADMIN"))
            .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"HIDDEN\"}")).andExpect(status().isOk());
        mvc.perform(patch(url).with(user("admin@onlinelearning.local").roles("ADMIN"))
            .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"DELETED\"}")).andExpect(status().isBadRequest());
        for (String invalid : new String[]{"{\"status\":\"INVALID\"}", "{\"status\":0}", "{broken-json", "{}"})
            mvc.perform(patch(url).with(user("admin@onlinelearning.local").roles("ADMIN"))
                .contentType(MediaType.APPLICATION_JSON).content(invalid)).andExpect(status().isBadRequest());
        c.setStatus(CommentStatus.DELETED); comments.flush();
        mvc.perform(patch(url).with(user("manager@onlinelearning.local").roles("MANAGER"))
            .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"VISIBLE\"}")).andExpect(status().isConflict());
    }
    @Test void moderationReadsPermitManagerAdminAndNeverExposeHiddenDataToStudent() throws Exception {
        Post p = createPost(true); PostComment hidden = comment(p, CommentStatus.HIDDEN, STUDENT);
        comment(p, CommentStatus.VISIBLE, STUDENT); comment(p, CommentStatus.DELETED, STUDENT);
        for (String role : new String[]{"MANAGER", "ADMIN"}) {
            String url = "/api/v1/" + role.toLowerCase() + "/posts/" + p.getId() + "/comments";
            mvc.perform(get(url).with(user(role.toLowerCase()+"@onlinelearning.local").roles(role)))
                .andExpect(status().isOk()).andExpect(jsonPath("totalElements").value(2));
            mvc.perform(get(url).param("status", "HIDDEN").with(user(role.toLowerCase()+"@onlinelearning.local").roles(role)))
                .andExpect(status().isOk()).andExpect(jsonPath("totalElements").value(1));
            mvc.perform(get(url + "/" + hidden.getId()).with(user(role.toLowerCase()+"@onlinelearning.local").roles(role)))
                .andExpect(status().isOk()).andExpect(jsonPath("canDelete").value(true));
            mvc.perform(get(url).with(user(STUDENT).roles("STUDENT"))).andExpect(status().isForbidden());
        }
    }
}

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
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import org.springframework.http.MediaType;

@SpringBootTest @ActiveProfiles("test") @AutoConfigureMockMvc @Transactional
class ManagerPostIntegrationTests {
    @Autowired MockMvc mvc;
    @Autowired PostRepository posts;
    @Autowired UserRepository users;
    @Autowired MediaAssetRepository media;
    @Autowired AuditLogRepository audit;
    @Autowired jakarta.persistence.EntityManager em;
    private static final String MANAGER = "manager@onlinelearning.local";
    private String body(String title, boolean published, Long image) {
        return "{\"title\":\"" + title + "\",\"category\":\"Java\",\"summary\":\"Tóm tắt\",\"content\":\"Nội dung\",\"published\":" + published
            + ",\"thumbnailMediaId\":" + image + "}";
    }
    private MediaAsset image(String email) {
        MediaAsset a = new MediaAsset(); a.setOwner(users.findByEmailIgnoreCase(email).orElseThrow());
        a.setPurpose("CONTENT_IMAGE"); a.setResourceType("image"); a.setFormat("png"); a.setDeliveryType("upload");
        a.setPublicId("post-image"); a.setSecureUrl("https://example.test/post.png"); return media.saveAndFlush(a);
    }
    private Post draft() {
        Post p = new Post(); p.setAuthor(users.findByEmailIgnoreCase(MANAGER).orElseThrow());
        p.setTitle("Draft"); p.setContent("Nội dung"); return posts.saveAndFlush(p);
    }
    @Test void uc21CreatesDraftAndPublishesOnlyCompletePostWithAuthorizedImage() throws Exception {
        mvc.perform(post("/api/v1/manager/posts").with(user(MANAGER).roles("MANAGER")).contentType(MediaType.APPLICATION_JSON).content(body("Draft", false, null)))
            .andExpect(status().isCreated()).andExpect(jsonPath("published").value(false)).andExpect(jsonPath("post.authorName").value("Online Learning Manager"));
        mvc.perform(post("/api/v1/manager/posts").with(user(MANAGER).roles("MANAGER")).contentType(MediaType.APPLICATION_JSON).content(body("Incomplete", true, null)))
            .andExpect(status().isConflict());
        mvc.perform(post("/api/v1/manager/posts").with(user(MANAGER).roles("MANAGER")).contentType(MediaType.APPLICATION_JSON).content(body("Valid", true, image(MANAGER).getId())))
            .andExpect(status().isCreated()).andExpect(jsonPath("published").value(true));
        mvc.perform(post("/api/v1/manager/posts").with(user(MANAGER).roles("MANAGER")).contentType(MediaType.APPLICATION_JSON).content(body("Wrong image", true, image("admin@onlinelearning.local").getId())))
            .andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/manager/posts").with(user(MANAGER).roles("MANAGER")).contentType(MediaType.APPLICATION_JSON).content(body(" ", false, null)))
            .andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/manager/posts").with(user(MANAGER).roles("MANAGER")).contentType(MediaType.APPLICATION_JSON).content("{broken-json"))
            .andExpect(status().isBadRequest());
    }
    @Test void uc22UpdatesPublicReadAndCanUnpublishWithoutChangingAuthor() throws Exception {
        Post p = draft(); Long authorId = p.getAuthor().getId();
        mvc.perform(put("/api/v1/manager/posts/" + p.getId()).with(user(MANAGER).roles("MANAGER")).contentType(MediaType.APPLICATION_JSON)
            .content(body("Published title", true, image(MANAGER).getId())))
            .andExpect(status().isOk());
        mvc.perform(get("/api/v1/public/posts/" + p.getId())).andExpect(status().isOk()).andExpect(jsonPath("post.title").value("Published title"));
        mvc.perform(put("/api/v1/manager/posts/" + p.getId()).with(user(MANAGER).roles("MANAGER")).contentType(MediaType.APPLICATION_JSON)
            .content(body("Updated draft", false, null))).andExpect(status().isOk());
        mvc.perform(get("/api/v1/public/posts/" + p.getId())).andExpect(status().isNotFound());
        assertThat(p.getAuthor().getId()).isEqualTo(authorId);
        assertThat(audit.findAll()).anyMatch(a -> "UPDATE_POST".equals(a.getAction()));
    }
    @Test void uc23SoftDeletesAndRetainsReferencedCommentsAndAudit() throws Exception {
        Post p = draft(); p.setPublished(true); posts.flush();
        PostComment c = new PostComment(); c.setPost(p); c.setAuthor(users.findByEmailIgnoreCase("student@onlinelearning.local").orElseThrow()); c.setContent("Retained"); em.persist(c); em.flush();
        mvc.perform(delete("/api/v1/manager/posts/" + p.getId()).with(user(MANAGER).roles("MANAGER"))).andExpect(status().isNoContent());
        assertThat(posts.findById(p.getId()).orElseThrow().isDeleted()).isTrue();
        assertThat(em.find(PostComment.class, c.getId())).isNotNull();
        mvc.perform(get("/api/v1/public/posts/" + p.getId())).andExpect(status().isNotFound());
        mvc.perform(put("/api/v1/manager/posts/" + p.getId()).with(user(MANAGER).roles("MANAGER")).contentType(MediaType.APPLICATION_JSON).content(body("Resurrect", false, null))).andExpect(status().isNotFound());
        assertThat(audit.findAll()).anyMatch(a -> "DELETE_POST".equals(a.getAction()));
    }
    @Test void guestStudentExpertAndAdminCannotManagePosts() throws Exception {
        mvc.perform(post("/api/v1/manager/posts").contentType(MediaType.APPLICATION_JSON).content(body("Denied", false, null))).andExpect(status().isUnauthorized());
        for (String role : new String[]{"STUDENT", "EXPERT", "ADMIN"}) {
            var actor = user(role.toLowerCase() + "@onlinelearning.local").roles(role);
            mvc.perform(post("/api/v1/manager/posts").with(actor).contentType(MediaType.APPLICATION_JSON).content(body("Denied", false, null))).andExpect(status().isForbidden());
            mvc.perform(put("/api/v1/manager/posts/1").with(actor).contentType(MediaType.APPLICATION_JSON).content(body("Denied", false, null))).andExpect(status().isForbidden());
            mvc.perform(delete("/api/v1/manager/posts/1").with(actor)).andExpect(status().isForbidden());
        }
    }
}

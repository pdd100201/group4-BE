package com.onlinelearning.service;

import com.onlinelearning.entity.Post;
import com.onlinelearning.repository.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest @ActiveProfiles("test") @AutoConfigureMockMvc @Transactional
class PublicPostIntegrationTests {
    @Autowired PostRepository posts;
    @Autowired UserRepository users;
    @Autowired MockMvc mvc;

    private Post post(String title, String category, boolean published, boolean deleted) {
        Post p = new Post(); p.setAuthor(users.findByEmailIgnoreCase("manager@onlinelearning.local").orElseThrow());
        p.setTitle(title); p.setCategory(category); p.setContent("Nội dung " + title); p.setSummary("Tóm tắt");
        p.setPublished(published); p.setDeleted(deleted); p.setUpdatedAt(Instant.parse("2026-10-11T10:00:00Z"));
        return posts.saveAndFlush(p);
    }
    @Test void homeAndPublicReadsNeverExposeDraftOrDeletedEvenThroughDirectId() throws Exception {
        Post visible = post("Public", "Java", true, false);
        Post draft = post("Draft secret", "Private category", false, false);
        Post deleted = post("Deleted secret", "Private category", true, true);
        mvc.perform(get("/api/v1/public/posts").param("size", "3")).andExpect(status().isOk())
            .andExpect(jsonPath("totalElements").value(1)).andExpect(jsonPath("content[0].id").value(visible.getId().intValue()))
            .andExpect(jsonPath("content[0].authorName").exists()).andExpect(jsonPath("content[0].author.email").doesNotExist());
        for (Post hidden : new Post[]{draft, deleted}) {
            mvc.perform(get("/api/v1/public/posts/" + hidden.getId())).andExpect(status().isNotFound());
        }
        mvc.perform(get("/api/v1/public/posts/categories")).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1));
    }
    @Test void filtersPagingAndStableOrderingAreAppliedInDatabase() throws Exception {
        Post first = post("Java 100%", "Code", true, false);
        Post second = post("Java second", "Code", true, false);
        post("Design", "Design", true, false);
        mvc.perform(get("/api/v1/public/posts").param("search", "Java").param("category", "Code").param("size", "1"))
            .andExpect(status().isOk()).andExpect(jsonPath("totalElements").value(2)).andExpect(jsonPath("totalPages").value(2))
            .andExpect(jsonPath("content[0].id").value(second.getId().intValue()));
        mvc.perform(get("/api/v1/public/posts").param("search", "Java").param("category", "Code").param("size", "1").param("page", "1"))
            .andExpect(status().isOk()).andExpect(jsonPath("content[0].id").value(first.getId().intValue()));
        mvc.perform(get("/api/v1/public/posts").param("search", "%"))
            .andExpect(status().isOk()).andExpect(jsonPath("totalElements").value(1));
        for (String size : new String[]{"0", "101", "bad"}) mvc.perform(get("/api/v1/public/posts").param("size", size)).andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/public/posts").param("page", "-1")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/public/posts/0")).andExpect(status().isBadRequest());
    }
}

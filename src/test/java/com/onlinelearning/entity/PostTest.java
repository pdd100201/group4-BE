package com.onlinelearning.entity;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PostTest {
    @Test void newPostsGetUniqueSlugsForRepeatedVietnameseTitles() {
        Post first = new Post();
        Post second = new Post();
        first.setTitle("Bài viết Đầu tiên!");
        second.setTitle(first.getTitle());
        first.created();
        second.created();
        assertTrue(first.getSlug().matches("bai-viet-dau-tien-[0-9a-f-]{36}"));
        assertNotEquals(first.getSlug(), second.getSlug());
    }

    @Test void generatedSlugsFitDatabaseAndSupportNonLatinTitles() {
        Post longTitle = new Post();
        longTitle.setTitle("a".repeat(255));
        longTitle.created();
        assertEquals(280, longTitle.getSlug().length());
        Post nonLatin = new Post();
        nonLatin.setTitle("日本語");
        nonLatin.created();
        assertTrue(nonLatin.getSlug().startsWith("post-"));
    }

    @Test void existingSlugsRemainStable() {
        Post post = new Post();
        post.setTitle("New title");
        post.setSlug("demo-blog-1");
        post.created();
        assertEquals("demo-blog-1", post.getSlug());
    }
}

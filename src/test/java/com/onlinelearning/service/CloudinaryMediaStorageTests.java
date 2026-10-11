package com.onlinelearning.service;
import com.onlinelearning.api.CloudinaryMediaStorage;
import com.onlinelearning.exception.ApiException;
import org.junit.jupiter.api.Test;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;
class CloudinaryMediaStorageTests {
    @Test void missingConfigurationIsExplicit() {
        var storage = new CloudinaryMediaStorage("", "", "");
        assertEquals(503, assertThrows(ApiException.class, () -> storage.upload(Path.of("unused"), "test", false)).status().value());
    }
    @Test void playbackIsAuthenticatedSignedAndTimeLimited() {
        var storage = new CloudinaryMediaStorage("demo", "test-key", "test-secret");
        String url = storage.playback("online-learning/test", "mp4");
        assertTrue(url.startsWith("https://")); assertTrue(url.contains("signature="));
        assertTrue(url.contains("expires_at=")); assertTrue(url.contains("type=authenticated")); assertFalse(url.contains("test-secret"));
    }
}

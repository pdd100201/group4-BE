package com.onlinelearning.api;

import com.cloudinary.*;
import com.onlinelearning.exception.ApiException;
import org.junit.jupiter.api.Test;
import java.net.http.HttpClient;
import java.nio.file.Path;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class CloudinaryChatStorageTests {
    @Test void missingConfigurationFailsExplicitly() {
        var storage = new CloudinaryChatStorage("", "", "");
        assertEquals(503, assertThrows(ApiException.class, () -> storage.upload(Path.of("unused"), "chat", false)).status().value());
    }

    @Test void signedImageAndVideoDownloadsAreAuthenticatedAndExpire() {
        var storage = new CloudinaryChatStorage("demo", "fake-key", "fake-secret");
        for (String type : new String[]{"image", "video"}) {
            String url = storage.signedDownload("chat/test", type, type.equals("image") ? "png" : "mp4");
            assertTrue(url.startsWith("https://api.cloudinary.com/"));
            assertTrue(url.contains("type=authenticated")); assertTrue(url.contains("signature="));
            assertTrue(url.contains("expires_at=")); assertFalse(url.contains("fake-secret"));
        }
    }

    @Test void imagesAndVideosAreUploadedWithAuthenticatedDelivery() throws Exception {
        Cloudinary cloudinary = mock(Cloudinary.class); Uploader uploader = mock(Uploader.class);
        when(cloudinary.uploader()).thenReturn(uploader);
        Map<String, Object> response = Map.of("public_id", "chat/test", "resource_type", "image", "type", "authenticated",
            "format", "png", "secure_url", "https://res.cloudinary.com/demo/image/authenticated/test.png", "bytes", 42L);
        when(uploader.upload(any(), anyMap())).thenReturn(response);
        when(uploader.uploadLarge(any(), anyMap())).thenReturn(response);
        var storage = new CloudinaryChatStorage(cloudinary, HttpClient.newHttpClient(), true);
        storage.upload(Path.of("test.png"), "chat", false);
        storage.upload(Path.of("test.mp4"), "chat", true);
        verify(uploader).upload(any(), argThat(options -> "authenticated".equals(options.get("type")) && "image".equals(options.get("resource_type"))));
        verify(uploader).uploadLarge(any(), argThat(options -> "authenticated".equals(options.get("type")) && "video".equals(options.get("resource_type"))));
    }
}

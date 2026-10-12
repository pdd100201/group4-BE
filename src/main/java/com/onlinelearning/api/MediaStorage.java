package com.onlinelearning.api;
import java.nio.file.Path;
public interface MediaStorage {
    record StoredMedia(String publicId, String resourceType, String deliveryType, String format, String secureUrl, long bytes) {}
    StoredMedia upload(Path file, String folder, boolean video);
    String playback(String publicId, String format);
}

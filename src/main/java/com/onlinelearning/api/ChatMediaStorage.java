package com.onlinelearning.api;

import org.springframework.core.io.Resource;
import java.nio.file.Path;

/** Private chat storage is separate from publicly delivered course/avatar images. */
public interface ChatMediaStorage {
    MediaStorage.StoredMedia upload(Path file, String folder, boolean video);
    Resource download(String publicId, String resourceType, String format);
    void delete(String publicId, String resourceType);
}

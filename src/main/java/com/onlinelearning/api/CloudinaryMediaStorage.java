package com.onlinelearning.api;
import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import com.onlinelearning.exception.ApiException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Component
public class CloudinaryMediaStorage implements MediaStorage {
    private final Cloudinary cloudinary;
    private final boolean configured;
    public CloudinaryMediaStorage(@Value("${app.cloudinary.cloud-name:}") String name, @Value("${app.cloudinary.api-key:}") String key,
            @Value("${app.cloudinary.api-secret:}") String secret) {
        configured = !name.isBlank() && !key.isBlank() && !secret.isBlank();
        cloudinary = new Cloudinary(ObjectUtils.asMap("cloud_name", name, "api_key", key, "api_secret", secret, "secure", true));
    }
    private void ready() { if (!configured) throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "Chưa cấu hình Cloudinary trên server."); }
    public StoredMedia upload(Path file, String folder, boolean video) {
        ready();
        try {
            Map<String, Object> options = new java.util.HashMap<>();
            options.put("public_id", folder + "/" + UUID.randomUUID());
            options.put("resource_type", video ? "video" : "image");
            options.put("type", video ? "authenticated" : "upload");
            options.put("overwrite", false);
            options.put("allowed_formats", video ? new String[]{"mp4", "webm"} : new String[]{"jpg", "png", "webp"});
            options.put("timeout", 180000);
            Map<?, ?> result = video ? cloudinary.uploader().uploadLarge(file.toFile(), options) : cloudinary.uploader().upload(file.toFile(), options);
            return new StoredMedia((String) result.get("public_id"), (String) result.get("resource_type"),
                (String) result.get("type"), (String) result.get("format"), (String) result.get("secure_url"), ((Number) result.get("bytes")).longValue());
        } catch (Exception e) { throw new ApiException(HttpStatus.BAD_GATEWAY, "Không thể tải tệp lên Cloudinary. Vui lòng thử lại."); }
    }
    public String playback(String publicId, String format) {
        ready();
        try {
            return cloudinary.privateDownload(publicId, format, ObjectUtils.asMap("resource_type", "video", "type", "authenticated", "attachment", false,
                "expires_at", Instant.now().plusSeconds(3600).getEpochSecond()));
        } catch (Exception e) { throw new ApiException(HttpStatus.BAD_GATEWAY, "Không thể tạo liên kết xem video."); }
    }
}

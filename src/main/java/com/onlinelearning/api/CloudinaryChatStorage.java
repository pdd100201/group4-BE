package com.onlinelearning.api;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import com.onlinelearning.exception.ApiException;
import org.springframework.beans.factory.annotation.*;
import org.springframework.core.io.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import java.io.IOException;
import java.net.URI;
import java.net.http.*;
import java.nio.file.Path;
import java.time.*;
import java.util.*;

@Component
public class CloudinaryChatStorage implements ChatMediaStorage {
    private final Cloudinary cloudinary;
    private final HttpClient http;
    private final boolean configured;

    @Autowired
    public CloudinaryChatStorage(@Value("${app.cloudinary.cloud-name:}") String name,
            @Value("${app.cloudinary.api-key:}") String key, @Value("${app.cloudinary.api-secret:}") String secret) {
        this(new Cloudinary(ObjectUtils.asMap("cloud_name", name, "api_key", key, "api_secret", secret, "secure", true)),
            HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build(),
            !name.isBlank() && !key.isBlank() && !secret.isBlank());
    }

    // Package-visible constructor keeps provider behavior testable without real credentials/network.
    CloudinaryChatStorage(Cloudinary cloudinary, HttpClient http, boolean configured) {
        this.cloudinary = cloudinary; this.http = http; this.configured = configured;
    }

    private void ready() {
        if (!configured) throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "Chưa cấu hình Cloudinary trên server.");
    }

    @Override public MediaStorage.StoredMedia upload(Path file, String folder, boolean video) {
        ready();
        try {
            Map<String, Object> options = new HashMap<>();
            options.put("public_id", folder + "/" + UUID.randomUUID());
            options.put("resource_type", video ? "video" : "image");
            options.put("type", "authenticated"); // Protect BOTH chat images and videos.
            options.put("overwrite", false);
            options.put("allowed_formats", video ? new String[]{"mp4", "webm"} : new String[]{"jpg", "png", "webp", "gif"});
            options.put("timeout", 180000);
            Map<?, ?> result = video ? cloudinary.uploader().uploadLarge(file.toFile(), options)
                : cloudinary.uploader().upload(file.toFile(), options);
            if (!"authenticated".equals(result.get("type"))) throw new IllegalStateException("Unexpected delivery type");
            return new MediaStorage.StoredMedia((String) result.get("public_id"), (String) result.get("resource_type"),
                (String) result.get("type"), (String) result.get("format"), (String) result.get("secure_url"), ((Number) result.get("bytes")).longValue());
        } catch (Exception ex) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "Không thể tải tệp chat lên Cloudinary. Vui lòng thử lại.");
        }
    }

    public String signedDownload(String publicId, String resourceType, String format) {
        ready();
        if (!Set.of("image", "video").contains(resourceType)) throw new ApiException(HttpStatus.BAD_GATEWAY, "Loại tệp Cloudinary không hợp lệ.");
        try {
            return cloudinary.privateDownload(publicId, format, ObjectUtils.asMap("resource_type", resourceType,
                "type", "authenticated", "attachment", false, "expires_at", Instant.now().plusSeconds(60).getEpochSecond()));
        } catch (Exception ex) { throw new ApiException(HttpStatus.BAD_GATEWAY, "Không thể tạo liên kết tải tệp chat."); }
    }

    @Override public Resource download(String publicId, String resourceType, String format) {
        // Called only after the chat service has verified ownership/conversation membership.
        URI uri = URI.create(signedDownload(publicId, resourceType, format));
        if (!"https".equals(uri.getScheme()) || !"api.cloudinary.com".equals(uri.getHost()))
            throw new ApiException(HttpStatus.BAD_GATEWAY, "Địa chỉ Cloudinary không hợp lệ.");
        try {
            var request = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(60)).GET().build();
            var response = http.send(request, HttpResponse.BodyHandlers.ofInputStream());
            if (response.statusCode() != 200) {
                response.body().close(); throw new IOException("Cloudinary download failed");
            }
            // Keep signed URLs server-side; stream bytes through the existing protected endpoint.
            return new InputStreamResource(response.body());
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt(); throw new ApiException(HttpStatus.BAD_GATEWAY, "Tải tệp bị gián đoạn.");
        } catch (IOException ex) { throw new ApiException(HttpStatus.BAD_GATEWAY, "Không thể tải tệp từ Cloudinary."); }
    }

    @Override public void delete(String publicId, String resourceType) {
        ready();
        try { cloudinary.uploader().destroy(publicId, ObjectUtils.asMap("resource_type", resourceType, "type", "authenticated", "invalidate", true)); }
        catch (Exception ex) { throw new ApiException(HttpStatus.BAD_GATEWAY, "Không thể dọn tệp Cloudinary chưa được lưu."); }
    }
}

package com.onlinelearning.service;

import com.onlinelearning.dto.chat.ChatAttachmentResponse;
import com.onlinelearning.api.ChatMediaStorage;
import com.onlinelearning.api.MediaStorage.StoredMedia;
import com.onlinelearning.entity.*;
import com.onlinelearning.exception.ApiException;
import com.onlinelearning.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;

@Service @RequiredArgsConstructor
public class ChatAttachmentService {
    private final ChatIdentityService identity;
    private final ChatAttachmentRepository files;
    private final ChatMessageRepository messages;
    private final UserRepository users;
    private final ChatMediaStorage storage;
    @Value("${app.chat.storage-dir:./uploads/chat}") private String storageDir;
    @Value("${app.cloudinary.folder:online-learning}") private String cloudFolder;

    public ChatAttachmentResponse upload(MultipartFile file, Long recipientId) {
        User owner = identity.current();
        if (Objects.equals(owner.getId(), recipientId)) throw bad("Không thể gửi tệp cho chính mình.");
        User recipient = recipientId == null ? null : users.findById(recipientId)
            .filter(u -> u.isEnabled() && !u.isBlocked()).orElseThrow(() -> bad("Người nhận không khả dụng."));
        if (file.isEmpty()) throw bad("Hãy chọn ảnh hoặc video.");
        if (file.getSize() > 25L * 1024 * 1024) throw new ApiException(HttpStatus.PAYLOAD_TOO_LARGE, "Video tối đa 25 MB, ảnh tối đa 5 MB.");
        Path path = null;
        StoredMedia uploaded = null;
        try {
            String type;
            try (InputStream input = file.getInputStream()) { type = detect(input.readNBytes(32)); }
            if (type.startsWith("image/") && file.getSize() > 5L * 1024 * 1024)
                throw new ApiException(HttpStatus.PAYLOAD_TOO_LARGE, "Ảnh tối đa 5 MB.");
            // Disk is temporary only. No database transaction is held during the remote upload.
            path = Files.createTempFile("ols-chat-", ".upload");
            file.transferTo(path);
            uploaded = storage.upload(path, cloudFolder + "/chat/" + owner.getId(), type.startsWith("video/"));
            ChatAttachment attachment = new ChatAttachment();
            attachment.setOwner(owner); attachment.setRecipient(recipient); attachment.setStorageKey(UUID.randomUUID().toString());
            attachment.setCloudPublicId(uploaded.publicId()); attachment.setCloudResourceType(uploaded.resourceType());
            attachment.setCloudFormat(uploaded.format()); attachment.setCloudDeliveryType(uploaded.deliveryType());
            String name = Objects.requireNonNullElse(file.getOriginalFilename(), "attachment").replaceAll("[\\p{Cntrl}\\\\/]", "_");
            if (name.isBlank()) name = "attachment";
            attachment.setOriginalName(name.substring(0, Math.min(200, name.length())));
            attachment.setContentType(type); attachment.setBytes(uploaded.bytes()); attachment.setCreatedAt(Instant.now());
            return ChatAttachmentResponse.from(files.saveAndFlush(attachment));
        } catch (IOException | RuntimeException ex) {
            if (uploaded != null) try { storage.delete(uploaded.publicId(), uploaded.resourceType()); } catch (RuntimeException ignored) { }
            if (ex instanceof ApiException api) throw api;
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "Không thể tải tệp lên. Vui lòng thử lại.");
        } finally {
            if (path != null) try { Files.deleteIfExists(path); } catch (IOException ignored) { }
        }
    }

    public record Download(Resource resource, String contentType, String name) { }
    @Transactional(readOnly = true)
    public Download download(Long id) {
        Long me = identity.current().getId();
        ChatAttachment attachment = files.findById(id).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Tệp không tồn tại."));
        if (!attachment.getOwner().getId().equals(me)) {
            // A staged upload is owner-only. Other users need membership of its committed message.
            ChatMessage message = messages.findByAttachmentId(id).orElseThrow(() -> new ApiException(HttpStatus.FORBIDDEN, "Bạn không có quyền xem tệp."));
            if (message.getRecipient() != null && !message.getRecipient().getId().equals(me))
                throw new ApiException(HttpStatus.FORBIDDEN, "Bạn không có quyền xem tệp.");
        }
        if (attachment.getCloudPublicId() != null) {
            if (!"authenticated".equals(attachment.getCloudDeliveryType()))
                throw new ApiException(HttpStatus.BAD_GATEWAY, "Cấu hình bảo vệ tệp không hợp lệ.");
            return new Download(storage.download(attachment.getCloudPublicId(), attachment.getCloudResourceType(), attachment.getCloudFormat()),
                attachment.getContentType(), attachment.getOriginalName());
        }
        // Legacy reads remain available while old uploads are being migrated; new uploads never use this path.
        Path root = Path.of(storageDir).toAbsolutePath().normalize();
        Path path = root.resolve(attachment.getStorageKey()).normalize();
        if (!path.startsWith(root) || !Files.isRegularFile(path)) throw new ApiException(HttpStatus.NOT_FOUND, "Tệp hiện không khả dụng.");
        return new Download(new FileSystemResource(path), attachment.getContentType(), attachment.getOriginalName());
    }

    // Determine the supported media type from bytes, never trust a browser-supplied MIME type/extension.
    private String detect(byte[] b) {
        if (b.length >= 8 && Arrays.equals(Arrays.copyOf(b, 8), new byte[]{(byte)137,80,78,71,13,10,26,10})) return "image/png";
        if (b.length >= 3 && b[0] == (byte)255 && b[1] == (byte)216 && b[2] == (byte)255) return "image/jpeg";
        if (b.length >= 12 && ascii(b, 0, 4).equals("RIFF") && ascii(b, 8, 4).equals("WEBP")) return "image/webp";
        if (b.length >= 6 && (ascii(b, 0, 6).equals("GIF87a") || ascii(b, 0, 6).equals("GIF89a"))) return "image/gif";
        if (b.length >= 12 && ascii(b, 4, 4).equals("ftyp")) return "video/mp4";
        if (b.length >= 4 && b[0] == 0x1a && b[1] == 0x45 && b[2] == (byte)0xdf && b[3] == (byte)0xa3) return "video/webm";
        throw bad("Chỉ hỗ trợ ảnh JPG/PNG/WebP/GIF và video MP4/WebM.");
    }
    private String ascii(byte[] b, int offset, int length) { return new String(b, offset, length, StandardCharsets.US_ASCII); }
    private static ApiException bad(String text) { return new ApiException(HttpStatus.BAD_REQUEST, text); }
}

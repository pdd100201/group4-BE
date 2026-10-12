package com.onlinelearning.service;
import com.onlinelearning.api.MediaStorage;
import com.onlinelearning.entity.*;
import com.onlinelearning.exception.ApiException;
import com.onlinelearning.repository.*;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import java.nio.file.*;
import java.io.*;
import java.time.Instant;
import java.util.Arrays;

@Service @RequiredArgsConstructor @Transactional
public class MediaService {
    private final MediaStorage storage;
    private final MediaAssetRepository assets;
    private final UserRepository users;
    private final CourseRepository courses;
    private final EntityManager em;
    @Value("${app.cloudinary.folder:online-learning}") private String folder;
    public record UploadResponse(Long id, String url, String resourceType, long bytes) {}
    public UploadResponse upload(String email, MultipartFile file, String purpose, Long courseId) {
        User user = users.findByEmailIgnoreCase(email).orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Tài khoản không tồn tại."));
        boolean video = "LESSON_VIDEO".equals(purpose);
        Course course = null;
        if ("COURSE_IMAGE".equals(purpose) || video) {
            if (courseId == null || user.getRoles().stream().noneMatch(r -> "EXPERT".equals(r.getCode()))) throw new ApiException(HttpStatus.FORBIDDEN, "Chỉ Expert sở hữu khóa học được tải nội dung lên.");
            course = courses.findForUpdate(courseId).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Không tìm thấy khóa học."));
            if (!course.getExpert().getId().equals(user.getId())) throw new ApiException(HttpStatus.FORBIDDEN, "Khóa học không thuộc bạn.");
            if (course.getStatus() != CourseStatus.DRAFT && course.getStatus() != CourseStatus.REJECTED
                && !(course.getStatus() == CourseStatus.PUBLISHED && "COURSE_IMAGE".equals(purpose)))
                throw new ApiException(HttpStatus.CONFLICT, "Khóa học hiện không cho phép thay đổi nội dung này.");
        } else if ("CONTENT_IMAGE".equals(purpose)) {
            if (user.getRoles().stream().noneMatch(r -> "MANAGER".equals(r.getCode()) || "ADMIN".equals(r.getCode()))) throw new ApiException(HttpStatus.FORBIDDEN, "Bạn không có quyền tải ảnh nội dung.");
        } else if (!"AVATAR".equals(purpose)) throw new ApiException(HttpStatus.BAD_REQUEST, "Mục đích tải lên không hợp lệ.");
        validate(file, video);
        Path temp = null;
        try {
            temp = Files.createTempFile("ols-media-", ".upload");
            file.transferTo(temp);
            var result = storage.upload(temp, folder + "/" + purpose.toLowerCase() + "/" + user.getId(), video);
            MediaAsset asset = new MediaAsset(); asset.setOwner(user); asset.setCourse(course); asset.setPurpose(purpose);
            asset.setPublicId(result.publicId()); asset.setResourceType(result.resourceType()); asset.setDeliveryType(result.deliveryType());
            asset.setFormat(result.format()); asset.setSecureUrl(result.secureUrl()); asset.setBytes(result.bytes()); asset.setCreatedAt(Instant.now()); assets.save(asset);
            return new UploadResponse(asset.getId(), video ? null : asset.getSecureUrl(), asset.getResourceType(), asset.getBytes());
        } catch (IOException e) { throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "Không thể xử lý tệp tải lên."); }
        finally { if (temp != null) try { Files.deleteIfExists(temp); } catch (IOException ignored) { /* OS temporary directory cleanup can remove leftover files. */ } }
    }
    private void validate(MultipartFile file, boolean video) {
        if (file.isEmpty()) throw new ApiException(HttpStatus.BAD_REQUEST, "Hãy chọn tệp để tải lên.");
        if (file.getSize() > (video ? 100L : 10L) * 1024 * 1024) throw new ApiException(HttpStatus.PAYLOAD_TOO_LARGE, video ? "Video tối đa 100 MB." : "Ảnh tối đa 10 MB.");
        try (InputStream in = file.getInputStream()) {
            byte[] b = in.readNBytes(16);
            boolean png = b.length >= 8 && Arrays.equals(Arrays.copyOf(b, 8), new byte[]{(byte)137,80,78,71,13,10,26,10});
            boolean jpg = b.length >= 3 && b[0] == (byte)255 && b[1] == (byte)216 && b[2] == (byte)255;
            boolean webp = b.length >= 12 && new String(b,0,4,java.nio.charset.StandardCharsets.US_ASCII).equals("RIFF") && new String(b,8,4,java.nio.charset.StandardCharsets.US_ASCII).equals("WEBP");
            boolean mp4 = b.length >= 12 && new String(b,4,4,java.nio.charset.StandardCharsets.US_ASCII).equals("ftyp");
            boolean webm = b.length >= 4 && b[0] == (byte)0x1a && b[1] == (byte)0x45 && b[2] == (byte)0xdf && b[3] == (byte)0xa3;
            if (!(video ? mp4 || webm : png || jpg || webp)) throw new ApiException(HttpStatus.BAD_REQUEST, video ? "Chỉ hỗ trợ video MP4/WebM." : "Chỉ hỗ trợ ảnh JPG/PNG/WebP.");
        } catch (IOException e) { throw new ApiException(HttpStatus.BAD_REQUEST, "Không thể đọc tệp."); }
    }
    public MediaAsset attachment(Long id, String email, Long courseId, String purpose) {
        if (id == null) return null;
        MediaAsset a = assets.findById(id).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Tệp không tồn tại."));
        if (!a.getOwner().getEmail().equalsIgnoreCase(email) || !purpose.equals(a.getPurpose()) || a.getCourse() == null || !a.getCourse().getId().equals(courseId))
            throw new ApiException(HttpStatus.FORBIDDEN, "Tệp không thuộc đúng khóa học hoặc không đúng loại.");
        return a;
    }
    @Transactional(readOnly=true)
    public String playback(Long id, String email) {
        MediaAsset a = assets.findById(id).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Video không tồn tại."));
        User user = users.findByEmailIgnoreCase(email).orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Tài khoản không tồn tại."));
        if (!"video".equals(a.getResourceType())) throw new ApiException(HttpStatus.BAD_REQUEST, "Tệp không phải video.");
        boolean allowed = a.getOwner().getId().equals(user.getId()) || user.getRoles().stream().anyMatch(r -> "MANAGER".equals(r.getCode()));
        if (!allowed && a.getCourse() != null && a.getCourse().getStatus() == CourseStatus.PUBLISHED && user.getRoles().stream().anyMatch(r -> "STUDENT".equals(r.getCode()))) {
            Long count = em.createQuery("select count(r) from CourseRegistration r where r.student.id=:user and r.course.id=:course and r.status=:status and r.validFrom<=:now and (r.lifetimeSnapshot=true or r.validUntil>:now)", Long.class)
                .setParameter("user", user.getId()).setParameter("course", a.getCourse().getId()).setParameter("status", RegistrationStatus.ACTIVE).setParameter("now", Instant.now()).getSingleResult();
            Long linked = em.createQuery("select count(l) from Lesson l where l.video.id=:id and l.active=true", Long.class).setParameter("id", id).getSingleResult();
            allowed = count > 0 && linked > 0;
        }
        if (!allowed) throw new ApiException(HttpStatus.FORBIDDEN, "Bạn không có quyền xem video này.");
        return storage.playback(a.getPublicId(), a.getFormat());
    }
}

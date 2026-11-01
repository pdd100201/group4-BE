package com.onlinelearning.service;

import com.onlinelearning.api.ChatMediaStorage;
import com.onlinelearning.api.MediaStorage.StoredMedia;
import com.onlinelearning.repository.ChatAttachmentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import java.nio.file.*;

@Service @RequiredArgsConstructor @Slf4j
public class ChatAttachmentMigrationService {
    private final ChatAttachmentRepository files;
    private final ChatMediaStorage storage;
    private final TransactionTemplate transactions;
    @Value("${app.chat.storage-dir:./uploads/chat}") private String storageDir;
    @Value("${app.cloudinary.folder:online-learning}") private String cloudFolder;
    public record Result(int migrated, int failed) { }

    public Result migrateLocal() {
        int migrated = 0, failed = 0; long after = 0;
        while (true) {
            var batch = files.findByCloudPublicIdIsNullAndIdGreaterThanOrderByIdAsc(after, PageRequest.of(0, 50));
            if (batch.isEmpty()) break;
            for (var file : batch) {
                after = file.getId(); StoredMedia uploaded = null;
                try {
                    Path root = Path.of(storageDir).toAbsolutePath().normalize();
                    Path path = root.resolve(file.getStorageKey()).normalize();
                    if (!path.startsWith(root) || !Files.isRegularFile(path)) throw new IllegalStateException("Missing legacy file");
                    uploaded = storage.upload(path, cloudFolder + "/chat/migrated", file.getContentType().startsWith("video/"));
                    StoredMedia saved = uploaded;
                    // Upload outside the transaction; lock only the metadata update so reruns/concurrent runners are safe.
                    transactions.executeWithoutResult(status -> {
                        var current = files.findForUpdate(file.getId()).orElseThrow();
                        if (current.getCloudPublicId() != null) throw new IllegalStateException("Already migrated concurrently");
                        current.setCloudPublicId(saved.publicId()); current.setCloudResourceType(saved.resourceType());
                        current.setCloudFormat(saved.format()); current.setCloudDeliveryType(saved.deliveryType()); current.setBytes(saved.bytes());
                        files.saveAndFlush(current);
                    });
                    migrated++;
                } catch (RuntimeException ex) {
                    if (uploaded != null) try { storage.delete(uploaded.publicId(), uploaded.resourceType()); } catch (RuntimeException ignored) { }
                    failed++; log.warn("Chat attachment {} migration failed ({}); original file/metadata preserved", file.getId(), ex.getClass().getSimpleName());
                }
            }
        }
        return new Result(migrated, failed);
    }
}

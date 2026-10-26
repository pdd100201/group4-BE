package com.onlinelearning.service;

import com.onlinelearning.api.*;
import com.onlinelearning.entity.ChatAttachment;
import com.onlinelearning.repository.ChatAttachmentRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionTemplate;
import java.nio.file.*;
import java.util.*;
import java.util.function.Consumer;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class ChatAttachmentMigrationTests {
    @TempDir Path directory;
    @Test void migrationPreservesOriginalAndSkipsMigratedRowsOnRerun() throws Exception {
        var files = mock(ChatAttachmentRepository.class); var cloud = mock(ChatMediaStorage.class);
        var transactions = mock(TransactionTemplate.class);
        doAnswer(call -> { ((Consumer<?>) call.getArgument(0)).accept(null); return null; }).when(transactions).executeWithoutResult(any());
        ChatAttachment file = new ChatAttachment(); file.setId(4L); file.setStorageKey("legacy-id"); file.setContentType("image/png");
        Files.write(directory.resolve("legacy-id"), new byte[]{1,2,3});
        doReturn(List.of(file), List.of()).when(files).findByCloudPublicIdIsNullAndIdGreaterThanOrderByIdAsc(eq(0L), any());
        doReturn(List.of()).when(files).findByCloudPublicIdIsNullAndIdGreaterThanOrderByIdAsc(eq(4L), any());
        when(files.findForUpdate(4L)).thenReturn(Optional.of(file));
        when(cloud.upload(any(), anyString(), eq(false))).thenReturn(new MediaStorage.StoredMedia("chat/cloud-id", "image", "authenticated", "png", "https://unused.invalid", 3));
        var service = new ChatAttachmentMigrationService(files, cloud, transactions);
        ReflectionTestUtils.setField(service, "storageDir", directory.toString()); ReflectionTestUtils.setField(service, "cloudFolder", "ols");
        assertEquals(1, service.migrateLocal().migrated()); assertEquals("chat/cloud-id", file.getCloudPublicId());
        assertTrue(Files.exists(directory.resolve("legacy-id"))); assertEquals(0, service.migrateLocal().migrated());
        verify(cloud, times(1)).upload(any(), anyString(), anyBoolean());
    }

    @Test void failedUploadLeavesLegacyMetadataAndDiskFileUntouched() throws Exception {
        var files = mock(ChatAttachmentRepository.class); var cloud = mock(ChatMediaStorage.class);
        ChatAttachment file = new ChatAttachment(); file.setId(1L); file.setStorageKey("old"); file.setContentType("video/mp4");
        Files.write(directory.resolve("old"), new byte[]{1});
        when(files.findByCloudPublicIdIsNullAndIdGreaterThanOrderByIdAsc(eq(0L), any())).thenReturn(List.of(file));
        when(files.findByCloudPublicIdIsNullAndIdGreaterThanOrderByIdAsc(eq(1L), any())).thenReturn(List.of());
        when(cloud.upload(any(), anyString(), anyBoolean())).thenThrow(new IllegalStateException("Provider unavailable"));
        var service = new ChatAttachmentMigrationService(files, cloud, mock(TransactionTemplate.class));
        ReflectionTestUtils.setField(service, "storageDir", directory.toString()); ReflectionTestUtils.setField(service, "cloudFolder", "ols");
        assertEquals(1, service.migrateLocal().failed()); assertNull(file.getCloudPublicId());
        assertTrue(Files.exists(directory.resolve("old"))); verify(files, never()).saveAndFlush(any());
    }
}

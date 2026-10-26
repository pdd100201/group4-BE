package com.onlinelearning.dto.chat;
import com.onlinelearning.entity.ChatAttachment;
public record ChatAttachmentResponse(Long id, String name, String contentType, long bytes) {
    public static ChatAttachmentResponse from(ChatAttachment file) {
        return new ChatAttachmentResponse(file.getId(), file.getOriginalName(), file.getContentType(), file.getBytes());
    }
}

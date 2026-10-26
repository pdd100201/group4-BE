package com.onlinelearning.dto.chat;

import com.onlinelearning.entity.ChatMessage;
import java.time.Instant;

public record ChatMessageResponse(Long id, ChatUserResponse sender, Long recipientId,
        String clientMessageId, String content, Instant sentAt, ChatAttachmentResponse attachment) {
    public static ChatMessageResponse from(ChatMessage message) {
        return new ChatMessageResponse(message.getId(), ChatUserResponse.from(message.getSender()),
            message.getRecipient() == null ? null : message.getRecipient().getId(),
            message.getClientMessageId(), message.getContent(), message.getSentAt(),
            message.getAttachment() == null ? null : ChatAttachmentResponse.from(message.getAttachment()));
    }
}

package com.onlinelearning.service;

import com.onlinelearning.dto.chat.*;
import com.onlinelearning.entity.*;
import com.onlinelearning.exception.ApiException;
import com.onlinelearning.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.util.*;

@Service @RequiredArgsConstructor
public class ChatService {
    private final ChatIdentityService identity;
    private final UserRepository users;
    private final ChatMessageRepository messages;
    private final ChatAttachmentRepository attachments;

    @Transactional(readOnly = true)
    public Page<ChatUserResponse> directory(String query, int page) {
        if (page < 0 || query.length() > 100) throw badRequest("Tìm kiếm không hợp lệ.");
        return users.chatDirectory(identity.current().getId(), escapeSearch(query.trim()),
            PageRequest.of(page, 30, Sort.by("fullName").ascending().and(Sort.by("id"))))
            .map(ChatUserResponse::from);
    }

    @Transactional(readOnly = true)
    public List<ChatMessageResponse> history(Long peerId, Long before) {
        Long me = identity.current().getId();
        if (before != null && before <= 0) throw badRequest("Mốc tin nhắn không hợp lệ.");
        if (peerId != null && (peerId <= 0 || peerId.equals(me) || !users.existsById(peerId)))
            throw badRequest("Người nhận không hợp lệ.");
        var page = PageRequest.of(0, 50);
        var rows = peerId == null ? messages.community(before, page) : messages.privateHistory(me, peerId, before, page);
        // IDs provide stable history pagination, including messages with identical timestamps.
        return rows.stream().map(ChatMessageResponse::from).sorted(Comparator.comparing(ChatMessageResponse::id)).toList();
    }

    @Transactional(readOnly = true)
    public List<ChatMessageResponse> search(Long peerId, String query, Long before) {
        Long me = identity.current().getId();
        if (query == null || query.isBlank() || query.length() > 100)
            throw badRequest("Từ khóa phải có từ 1 đến 100 ký tự.");
        if (before != null && before <= 0) throw badRequest("Mốc tin nhắn không hợp lệ.");
        if (peerId != null && (peerId <= 0 || peerId.equals(me) || !users.existsById(peerId)))
            throw badRequest("Người nhận không hợp lệ.");
        // Escape LIKE metacharacters: user input is a literal substring, not a database pattern.
        String pattern = "%" + escapeSearch(query.trim()) + "%";
        var page = PageRequest.of(0, 50);
        var rows = peerId == null ? messages.searchCommunity(pattern, before, page)
            : messages.searchPrivate(me, peerId, pattern, before, page);
        return rows.stream().map(ChatMessageResponse::from).toList();
    }

    private static String escapeSearch(String value) {
        return value.replace("!", "!!").replace("%", "!%").replace("_", "!_");
    }

    @Transactional
    public ChatMessageResponse send(Long senderId, Long recipientId, String content, String clientMessageId) {
        return send(senderId, recipientId, content, clientMessageId, null);
    }

    @Transactional
    public ChatMessageResponse send(Long senderId, Long recipientId, String content, String clientMessageId, Long attachmentId) {
        content = content == null ? "" : content.trim();
        if ((content.isBlank() && attachmentId == null) || content.length() > 2000)
            throw badRequest("Hãy nhập tin nhắn hoặc chọn ảnh/video. Nội dung tối đa 2000 ký tự.");
        try { UUID.fromString(clientMessageId); } catch (RuntimeException ex) {
            throw badRequest("Mã tin nhắn không hợp lệ.");
        }
        User sender = activeUser(senderId);
        if (senderId.equals(recipientId)) throw badRequest("Hãy chọn một người khác để trò chuyện.");
        User recipient = recipientId == null ? null : activeUser(recipientId);
        var existing = messages.findBySenderIdAndClientMessageId(senderId, clientMessageId);
        if (existing.isPresent()) {
            var old = existing.get();
            Long oldRecipient = old.getRecipient() == null ? null : old.getRecipient().getId();
            Long oldAttachment = old.getAttachment() == null ? null : old.getAttachment().getId();
            if (!Objects.equals(oldRecipient, recipientId) || !old.getContent().equals(content) || !Objects.equals(oldAttachment, attachmentId))
                throw new ApiException(HttpStatus.CONFLICT, "Mã tin nhắn đã được sử dụng.");
            return ChatMessageResponse.from(old);
        }
        ChatMessage message = new ChatMessage();
        if (attachmentId != null) {
            // Upload ownership alone is insufficient: the target conversation must also match.
            ChatAttachment file = attachments.findById(attachmentId).orElseThrow(() -> badRequest("Tệp không tồn tại."));
            Long target = file.getRecipient() == null ? null : file.getRecipient().getId();
            if (!file.getOwner().getId().equals(senderId) || !Objects.equals(target, recipientId))
                throw new ApiException(HttpStatus.FORBIDDEN, "Tệp không thuộc cuộc trò chuyện này.");
            if (messages.findByAttachmentId(attachmentId).isPresent()) throw badRequest("Tệp đã được gửi. Hãy tải lại nếu muốn gửi lần nữa.");
            message.setAttachment(file);
        }
        message.setSender(sender); message.setRecipient(recipient); message.setContent(content.trim());
        message.setSentAt(Instant.now()); message.setClientMessageId(clientMessageId);
        return ChatMessageResponse.from(messages.saveAndFlush(message));
    }

    private User activeUser(Long id) {
        if (id == null || id <= 0) throw badRequest("Tài khoản không hợp lệ.");
        return users.findById(id).filter(user -> user.isEnabled() && !user.isBlocked())
            .orElseThrow(() -> badRequest("Tài khoản không khả dụng."));
    }
    private static ApiException badRequest(String message) { return new ApiException(HttpStatus.BAD_REQUEST, message); }
}

package com.onlinelearning.controller;

import com.onlinelearning.dto.chat.*;
import com.onlinelearning.service.ChatService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController @RequestMapping("/api/v1/chat") @RequiredArgsConstructor
public class ChatController {
    private final ChatService service;
    private final com.onlinelearning.service.ChatAccessService access;
    private final com.onlinelearning.repository.UserRepository users;
    @GetMapping("/contact")
    public ChatContactResponse contact(@RequestParam Long peerId, @RequestParam Long courseId, org.springframework.security.core.Authentication actor) {
        var me = users.findByEmailIgnoreCase(actor.getName()).orElseThrow(() -> new com.onlinelearning.exception.ApiException(org.springframework.http.HttpStatus.UNAUTHORIZED, "Tài khoản không tồn tại."));
        var course = access.requireConversation(me.getId(), peerId, courseId);
        return new ChatContactResponse(courseId, course.getTitle(), ChatUserResponse.from(users.findById(peerId).orElseThrow()));
    }
    @GetMapping("/users")
    public Page<ChatContactResponse> users(@RequestParam(defaultValue = "") String query, @RequestParam(defaultValue = "0") int page) {
        return service.directory(query, page);
    }
    @GetMapping("/messages")
    public List<ChatMessageResponse> messages(@RequestParam Long peerId, @RequestParam Long courseId, @RequestParam(required = false) Long before) {
        return service.history(peerId, courseId, before);
    }
    @GetMapping("/messages/search")
    public List<ChatMessageResponse> search(@RequestParam Long peerId, @RequestParam Long courseId,
                                           @RequestParam String query,
                                           @RequestParam(required = false) Long before) {
        return service.search(peerId, courseId, query, before);
    }
}

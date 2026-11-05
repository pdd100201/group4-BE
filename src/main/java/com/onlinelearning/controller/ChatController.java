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
    @GetMapping("/users")
    public Page<ChatContactResponse> users(@RequestParam(defaultValue = "") String query, @RequestParam(defaultValue = "0") int page) {
        return service.directory(query, page);
    }
    @GetMapping("/messages")
    public List<ChatMessageResponse> messages(@RequestParam Long peerId, @RequestParam Long courseId,
                                               @RequestParam(required = false) Long before) {
        return service.history(peerId, courseId, before);
    }
    @GetMapping("/messages/search")
    public List<ChatMessageResponse> search(@RequestParam Long peerId, @RequestParam Long courseId,
                                           @RequestParam String query,
                                           @RequestParam(required = false) Long before) {
        return service.search(peerId, courseId, query, before);
    }
}

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
    public Page<ChatUserResponse> users(@RequestParam(defaultValue = "") String query, @RequestParam(defaultValue = "0") int page) {
        return service.directory(query, page);
    }
    @GetMapping("/messages")
    public List<ChatMessageResponse> messages(@RequestParam(required = false) Long peerId, @RequestParam(required = false) Long before) {
        return service.history(peerId, before);
    }
    @GetMapping("/messages/search")
    public List<ChatMessageResponse> search(@RequestParam(required = false) Long peerId,
                                           @RequestParam String query,
                                           @RequestParam(required = false) Long before) {
        return service.search(peerId, query, before);
    }
}

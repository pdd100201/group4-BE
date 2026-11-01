package com.onlinelearning.controller;
import com.onlinelearning.dto.chat.ChatAttachmentResponse;
import com.onlinelearning.service.ChatAttachmentService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.nio.charset.StandardCharsets;

@RestController @RequestMapping("/api/v1/chat/attachments") @RequiredArgsConstructor
public class ChatAttachmentController {
    private final ChatAttachmentService service;
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE) @ResponseStatus(HttpStatus.CREATED)
    public ChatAttachmentResponse upload(@RequestParam MultipartFile file, @RequestParam(required = false) Long recipientId) {
        return service.upload(file, recipientId);
    }
    @GetMapping("/{id}")
    public ResponseEntity<Resource> download(@PathVariable Long id) {
        var file = service.download(id);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).header("X-Content-Type-Options", "nosniff")
            .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.inline().filename(file.name(), StandardCharsets.UTF_8).build().toString())
            .contentType(MediaType.parseMediaType(file.contentType())).body(file.resource());
    }
}

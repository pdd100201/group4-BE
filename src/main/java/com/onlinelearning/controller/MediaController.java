package com.onlinelearning.controller;
import com.onlinelearning.service.MediaService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.HttpStatus;
import org.springframework.web.multipart.MultipartFile;
import java.util.Map;
@RestController @RequiredArgsConstructor @RequestMapping("/api/v1/media")
public class MediaController {
    private final MediaService media;
    @PostMapping(consumes="multipart/form-data") @ResponseStatus(HttpStatus.CREATED)
    public MediaService.UploadResponse upload(@RequestParam MultipartFile file, @RequestParam String purpose, @RequestParam(required=false) Long courseId, Authentication actor) { return media.upload(actor.getName(), file, purpose, courseId); }
    @GetMapping("/{id}/playback") public org.springframework.http.ResponseEntity<Map<String,String>> playback(@PathVariable Long id, Authentication actor) {
        return org.springframework.http.ResponseEntity.ok().cacheControl(org.springframework.http.CacheControl.noStore()).body(Map.of("url", media.playback(id, actor.getName())));
    }
}

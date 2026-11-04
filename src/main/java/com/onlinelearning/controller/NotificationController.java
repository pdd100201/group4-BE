package com.onlinelearning.controller;
import com.onlinelearning.service.NotificationService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/v1/notifications") @RequiredArgsConstructor
public class NotificationController {
 private final NotificationService notifications;
 public record ReadAllRequest(@NotNull @Positive Long throughId) {}
 @GetMapping public Page<NotificationService.View> list(Authentication actor,@RequestParam(defaultValue="false") boolean unreadOnly,
  @RequestParam(defaultValue="all") String category,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size){
  return notifications.list(actor.getName(),unreadOnly,category,page,size);
 }
 @GetMapping("/summary") public NotificationService.Summary summary(Authentication actor){return notifications.summary(actor.getName());}
 @PutMapping("/{id}/read") public void read(Authentication actor,@PathVariable Long id){notifications.read(actor.getName(),id);}
 @PutMapping("/read-all") public void all(Authentication actor,@Valid @RequestBody ReadAllRequest request){notifications.readAll(actor.getName(),request.throughId());}
}
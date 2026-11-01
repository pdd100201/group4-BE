package com.onlinelearning.controller;

import com.onlinelearning.common.ApiMessage;
import com.onlinelearning.dto.blog.NewsletterSubscriptionRequest;
import com.onlinelearning.service.NewsletterSubscriptionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/public/newsletter")
public class PublicNewsletterController {
    private final NewsletterSubscriptionService subscriptions;

    @PostMapping("/subscriptions")
    public ResponseEntity<ApiMessage> subscribe(@Valid @RequestBody NewsletterSubscriptionRequest request) {
        boolean created = subscriptions.subscribe(request.email());
        String message = created
            ? "Đăng ký nhận bản tin thành công."
            : "Email này đã đăng ký nhận bản tin rồi.";
        return ResponseEntity.status(created ? HttpStatus.CREATED : HttpStatus.OK).body(new ApiMessage(message));
    }
}

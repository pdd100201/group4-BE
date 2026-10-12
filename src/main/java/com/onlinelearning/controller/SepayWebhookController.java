package com.onlinelearning.controller;

import com.onlinelearning.api.SepayGateway;
import com.onlinelearning.dto.payment.SepayWebhook;
import com.onlinelearning.exception.ApiException;
import com.onlinelearning.service.PaymentService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Validator;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController @RequiredArgsConstructor
public class SepayWebhookController {
    private final SepayGateway gateway;
    private final PaymentService payments;
    private final ObjectMapper json;
    private final Validator validator;
    @PostMapping(value = "/api/v1/public/payments/sepay/webhook", consumes = "application/json")
    public Map<String, Boolean> receive(@RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestBody String body) {
        gateway.authenticate(authorization);
        if (body.length() > 16000) throw new ApiException(HttpStatus.BAD_REQUEST, "Webhook qua lon.");
        SepayWebhook event;
        try { event = json.readValue(body, SepayWebhook.class); }
        catch (JsonProcessingException ex) { throw new ApiException(HttpStatus.BAD_REQUEST, "Webhook khong hop le."); }
        if (event == null || !validator.validate(event).isEmpty()) throw new ApiException(HttpStatus.BAD_REQUEST, "Du lieu giao dich khong hop le.");
        payments.confirm(event);
        return Map.of("success", true);
    }
}

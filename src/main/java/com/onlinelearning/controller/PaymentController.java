package com.onlinelearning.controller;

import com.onlinelearning.dto.payment.*;
import com.onlinelearning.service.PaymentService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/student/registrations/{id}/payment")
public class PaymentController {
    private final PaymentService payments;
    @PostMapping public PaymentResponse initiate(Authentication actor, @PathVariable Long id) {
        return payments.initiate(actor.getName(), id);
    }
    @GetMapping public PaymentResponse status(Authentication actor, @PathVariable Long id) { return payments.status(actor.getName(), id); }
}

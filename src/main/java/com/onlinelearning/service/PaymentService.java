package com.onlinelearning.service;

import com.onlinelearning.dto.payment.*;

public interface PaymentService {
    PaymentResponse initiate(String email, Long registrationId);
    PaymentResponse status(String email, Long registrationId);
    void confirm(SepayWebhook webhook);
}

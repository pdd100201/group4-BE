package com.onlinelearning.exception;

import org.springframework.http.HttpStatus;

public class PaymentExpiredException extends ApiException {
    public PaymentExpiredException() {
        super(HttpStatus.CONFLICT, "Thanh toan da het han 5 phut. Giao dich muon can doi soat thu cong, khong tu dong cap quyen hoc.");
    }
}

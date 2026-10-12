package com.onlinelearning.api;

import com.onlinelearning.config.SepayConfig;
import com.onlinelearning.exception.ApiException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

@Component @RequiredArgsConstructor
public class SepayGateway {
    private final SepayConfig config;
    public boolean configured() {
        return Set.of("test", "live").contains(config.getMode()) && valid(config.getBank()) && valid(config.getAccountNumber())
                && valid(config.getAccountHolder()) && valid(config.getWebhookApiKey()) && config.getWebhookApiKey().length() >= 32
                && config.getAccountNumber().matches("[A-Za-z0-9]{1,40}") && config.qrAccountNumber().matches("[A-Za-z0-9]{1,40}")
                && config.getBank().matches("[A-Za-z0-9]{1,30}") && config.getDescriptionPrefix().matches("[A-Za-z0-9 ]{0,20}");
    }
    public void requireConfiguration() {
        if (!configured()) throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "Chua cau hinh SePay: ngan hang, tai khoan, chu tai khoan va khoa webhook.");
    }
    public void authenticate(String authorization) {
        requireConfiguration();
        String expected = "Apikey " + config.getWebhookApiKey();
        if (authorization == null || !MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8), authorization.getBytes(StandardCharsets.UTF_8)))
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Webhook SePay khong duoc xac thuc.");
    }
    public String qrUrl(String bank, String account, BigDecimal amount, String description) {
        return UriComponentsBuilder.fromUriString("https://qr.sepay.vn/img").queryParam("bank", bank)
                .queryParam("acc", account).queryParam("amount", amount.setScale(0).toPlainString())
                .queryParam("des", description).queryParam("template", "compact").build().encode().toUriString();
    }
    private boolean valid(String value) { return value != null && !value.isBlank() && !value.startsWith("PASTE_"); }
}

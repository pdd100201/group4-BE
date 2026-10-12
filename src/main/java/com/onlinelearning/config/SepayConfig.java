package com.onlinelearning.config;

import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;

@Configuration @Getter
@PropertySource(value = {"file:./group4-BE/sepay-local.properties", "file:./sepay-local.properties"}, ignoreResourceNotFound = true)
public class SepayConfig {
    @Value("${SEPAY_MODE:${sepay.mode:test}}") private String mode;
    @Value("${SEPAY_BANK:${sepay.bank:}}") private String bank;
    @Value("${SEPAY_ACCOUNT_NUMBER:${sepay.account-number:}}") private String accountNumber;
    @Value("${SEPAY_QR_ACCOUNT:${sepay.qr-account:}}") private String qrAccount;
    @Value("${SEPAY_ACCOUNT_HOLDER:${sepay.account-holder:}}") private String accountHolder;
    @Value("${SEPAY_WEBHOOK_API_KEY:${sepay.webhook-api-key:}}") private String webhookApiKey;
    @Value("${SEPAY_DESCRIPTION_PREFIX:${sepay.description-prefix:}}") private String descriptionPrefix;
    public boolean testMode() { return "test".equals(mode); }
    public String provider() { return testMode() ? "SEPAY_TEST" : "SEPAY"; }
    public String qrAccountNumber() { return qrAccount.isBlank() ? accountNumber : qrAccount; }
}

package com.onlinelearning.dto.payment;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;

@JsonIgnoreProperties(ignoreUnknown = true)
public record SepayWebhook(@NotNull @Positive Long id, @NotBlank @Size(max = 50) String gateway,
        @NotBlank @Size(max = 19) String transactionDate, @NotBlank @Size(max = 50) String accountNumber,
        @Size(max = 50) String subAccount, @Size(max = 100) String code, @NotBlank @Size(max = 2000) String content,
        @NotBlank @Size(max = 10) String transferType, @NotNull @Positive @Digits(integer = 12, fraction = 0) BigDecimal transferAmount) {}

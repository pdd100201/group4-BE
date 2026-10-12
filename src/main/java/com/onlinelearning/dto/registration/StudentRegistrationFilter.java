package com.onlinelearning.dto.registration;

import com.onlinelearning.entity.PackageDuration;
import com.onlinelearning.entity.RegistrationStatus;
import java.math.BigDecimal;

public record StudentRegistrationFilter(String q, RegistrationStatus status, PackageDuration packageDuration,
                                        BigDecimal minAmount, BigDecimal maxAmount) {}

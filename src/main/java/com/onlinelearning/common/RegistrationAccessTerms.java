package com.onlinelearning.common;

import java.time.*;
import java.util.Objects;

/** Pure calendar calculation for the payment module; does not grant access. */
public final class RegistrationAccessTerms {
    private RegistrationAccessTerms() {}
    public static Instant validUntil(Instant paidAt,Integer months,boolean lifetime) {
        Objects.requireNonNull(paidAt,"Successful payment time is required");
        if(lifetime) return null;
        if(months==null || (months!=1 && months!=12)) throw new IllegalArgumentException("Registration has no valid 1-month/1-year snapshot");
        return paidAt.atZone(ZoneOffset.UTC).plusMonths(months).toInstant();
    }
}

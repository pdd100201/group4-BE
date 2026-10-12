package com.onlinelearning.service;
import com.onlinelearning.common.RegistrationAccessTerms;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import static org.junit.jupiter.api.Assertions.*;
class RegistrationAccessTermsTests {
    @Test void monthAndYearUseCalendarBoundariesRatherThanFixedDays() {
        assertEquals(Instant.parse("2026-02-28T10:00:00Z"),RegistrationAccessTerms.validUntil(Instant.parse("2026-01-31T10:00:00Z"),1,false));
        assertEquals(Instant.parse("2025-02-28T10:00:00Z"),RegistrationAccessTerms.validUntil(Instant.parse("2024-02-29T10:00:00Z"),12,false));
    }
    @Test void lifetimeHasNoFakeExpiryAndUnknownLegacyTermsAreRejected() {
        assertNull(RegistrationAccessTerms.validUntil(Instant.now(),null,true));
        assertThrows(IllegalArgumentException.class,()->RegistrationAccessTerms.validUntil(Instant.now(),null,false));
    }
}

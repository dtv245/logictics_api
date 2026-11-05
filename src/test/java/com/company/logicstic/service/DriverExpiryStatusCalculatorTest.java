package com.company.logicstic.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.company.logicstic.dto.driverprofile.ExpiryStatus;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class DriverExpiryStatusCalculatorTest {
    private final DriverExpiryStatusCalculator calculator = new DriverExpiryStatusCalculator(30, "Asia/Ho_Chi_Minh");
    private final LocalDate today = LocalDate.of(2026, 10, 30);

    @Test
    void classifiesExpiredWarningWindowAndValidDates() {
        assertEquals(ExpiryStatus.EXPIRED, DriverExpiryStatusCalculator.status(today.minusDays(1), today, 30));
        assertEquals(ExpiryStatus.EXPIRING_SOON, DriverExpiryStatusCalculator.status(today, today, 30));
        assertEquals(ExpiryStatus.EXPIRING_SOON, DriverExpiryStatusCalculator.status(today.plusDays(30), today, 30));
        assertEquals(ExpiryStatus.VALID, DriverExpiryStatusCalculator.status(today.plusDays(31), today, 30));
        assertEquals(null, DriverExpiryStatusCalculator.status(null, today, 30));
    }

    @Test
    void translatesTimestampToVietnamCalendarDateAndUsesConfiguredThreshold() {
        var timestamp = OffsetDateTime.of(2026, 10, 29, 18, 0, 0, 0, ZoneOffset.UTC);
        assertEquals(today, timestamp.atZoneSameInstant(java.time.ZoneId.of("Asia/Ho_Chi_Minh")).toLocalDate());
        assertEquals(ExpiryStatus.EXPIRING_SOON, calculator.status(timestamp, today));
        assertEquals(30, calculator.warningDays());
    }
}

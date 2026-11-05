package com.company.logicstic.service;

import com.company.logicstic.dto.driverprofile.ExpiryStatus;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class DriverExpiryStatusCalculator {
    private final int warningDays;
    private final ZoneId zoneId;

    public DriverExpiryStatusCalculator(
            @Value("${app.driver-profile.expiry-warning-days:30}") int warningDays,
            @Value("${app.driver-profile.time-zone:Asia/Ho_Chi_Minh}") String timeZone) {
        if (warningDays < 0 || warningDays > 365) {
            throw new IllegalArgumentException("Driver profile expiry warning days must be between 0 and 365");
        }
        this.warningDays = warningDays;
        this.zoneId = ZoneId.of(timeZone);
    }

    public ExpiryStatus status(LocalDate expiryDate) {
        return status(expiryDate, LocalDate.now(zoneId), warningDays);
    }

    public ExpiryStatus status(LocalDate expiryDate, LocalDate today) {
        return status(expiryDate, today, warningDays);
    }

    public ExpiryStatus status(OffsetDateTime expiryDate) {
        return status(expiryDate, currentDate());
    }

    public ExpiryStatus status(OffsetDateTime expiryDate, LocalDate today) {
        return expiryDate == null ? null : status(expiryDate.atZoneSameInstant(zoneId).toLocalDate(), today, warningDays);
    }

    static ExpiryStatus status(LocalDate expiryDate, LocalDate today, int warningDays) {
        if (expiryDate == null) return null;
        if (expiryDate.isBefore(today)) return ExpiryStatus.EXPIRED;
        if (!expiryDate.isAfter(today.plusDays(warningDays))) return ExpiryStatus.EXPIRING_SOON;
        return ExpiryStatus.VALID;
    }

    public int warningDays() {
        return warningDays;
    }

    public LocalDate currentDate() {
        return LocalDate.now(zoneId);
    }

    public String timeZoneId() {
        return zoneId.getId();
    }
}

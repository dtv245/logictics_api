package com.company.logicstic.dto.document;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.company.logicstic.dto.driverprofile.ExpiryStatus;
import com.company.logicstic.entity.Document;
import com.company.logicstic.service.DriverExpiryStatusCalculator;
import java.time.LocalDate;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;

class DocumentViewExpiryStatusTest {
    @Test
    void documentViewIncludesConfiguredExpiryStatus() {
        var document = new Document();
        document.setExpiryDate(LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh")).plusDays(7));
        var calculator = new DriverExpiryStatusCalculator(30, "Asia/Ho_Chi_Minh");

        assertEquals(ExpiryStatus.EXPIRING_SOON, DocumentView.from(document, calculator).expiryStatus());
    }
}

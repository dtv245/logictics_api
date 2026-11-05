package com.company.logicstic.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.company.logicstic.dto.driverprofile.ExpiryStatus;
import com.company.logicstic.exception.BadRequestException;
import com.company.logicstic.repository.DriverExpiringItemRepository;
import com.company.logicstic.repository.DriverExpiringItemRepository.DriverExpiringItemRow;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DriverExpiryServiceTest {
    private DriverExpiringItemRepository repository;
    private DriverExpiryService service;

    @BeforeEach
    void setUp() {
        repository = mock(DriverExpiringItemRepository.class);
        service = new DriverExpiryService(repository,
                new DriverExpiryStatusCalculator(30, "Asia/Ho_Chi_Minh") {
                    @Override public LocalDate currentDate() { return LocalDate.of(2026, 10, 30); }
                });
    }

    @Test
    void listsPagedItemsAndCalculatesExpiryStatusWithoutSensitiveIdentifiers() {
        UUID driverId = UUID.randomUUID();
        var row = new DriverExpiringItemRow(UUID.randomUUID(), driverId, "Driver Fixture", "LICENSE", "GPLX",
                LocalDate.of(2026, 11, 5));
        when(repository.findPage(any(), any(), eq("LICENSE"), isNull(), eq("Asia/Ho_Chi_Minh"), any())).thenReturn(List.of(row));
        when(repository.count(any(), any(), eq("LICENSE"), isNull(), eq("Asia/Ho_Chi_Minh"))).thenReturn(1L);

        var page = service.list(30, "license", 1, 20);

        assertEquals(1, page.totalItems());
        assertEquals("LICENSE", page.items().getFirst().type());
        assertEquals(6, page.items().getFirst().daysUntilExpiry());
        assertEquals(ExpiryStatus.EXPIRING_SOON, page.items().getFirst().expiryStatus());
        verify(repository).findPage(eq(LocalDate.of(2026, 10, 30)), eq(LocalDate.of(2026, 11, 29)),
                eq("LICENSE"), isNull(), eq("Asia/Ho_Chi_Minh"), any());
    }

    @Test
    void rejectsOutOfRangeDaysAndUnknownType() {
        assertThrows(BadRequestException.class, () -> service.list(0, null, 1, 20));
        assertThrows(BadRequestException.class, () -> service.list(366, null, 1, 20));
        assertThrows(BadRequestException.class, () -> service.list(30, "BANK_ACCOUNT", 1, 20));
        verifyNoInteractions(repository);
    }
}
